package com.payment.authnet.service;

import com.payment.authnet.config.AuthorizeNetConfig;
import com.payment.authnet.config.RedisKeys;
import com.payment.authnet.exception.AuthorizeNetException;
import com.payment.authnet.model.CustomerProfileResult;
import com.payment.authnet.model.TransactionResult;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.authorize.Environment;
import net.authorize.api.contract.v1.*;
import net.authorize.api.controller.CreateCustomerProfileController;
import net.authorize.api.controller.CreateTransactionController;
import net.authorize.api.controller.DeleteCustomerProfileController;
import net.authorize.api.controller.base.ApiOperationBase;
import org.slf4j.MDC;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerProfileService {

    private final AuthorizeNetConfig config;
    private final RedisTemplate<String, String> redisTemplate;

    @PostConstruct
    public void init() {
        Environment env = "production".equalsIgnoreCase(config.getEnvironment())
                ? Environment.PRODUCTION
                : Environment.SANDBOX;
        ApiOperationBase.setEnvironment(env);
    }

    private MerchantAuthenticationType merchantAuth() {
        MerchantAuthenticationType auth = new MerchantAuthenticationType();
        auth.setName(config.getApiLoginId());
        auth.setTransactionKey(config.getTransactionKey());
        return auth;
    }

    @CircuitBreaker(name = "gatewayCircuitBreaker")
    @Retry(name = "gatewayRetry")
    public CustomerProfileResult createCustomerProfile(String userId, String email,
                                                        String cardNumber, String expirationDate,
                                                        String cardCode) {
        String last4 = cardNumber.substring(cardNumber.length() - 4);
        log.info("[correlationId={}] Creating Authorize.net profile for userId={}, cardLast4={}",
                MDC.get("correlationId"), userId, last4);
        try {
            CreditCardType creditCard = new CreditCardType();
            creditCard.setCardNumber(cardNumber);
            creditCard.setExpirationDate(expirationDate);
            creditCard.setCardCode(cardCode);

            PaymentType paymentType = new PaymentType();
            paymentType.setCreditCard(creditCard);

            CustomerPaymentProfileType paymentProfile = new CustomerPaymentProfileType();
            paymentProfile.setCustomerType(CustomerTypeEnum.INDIVIDUAL);
            paymentProfile.setPayment(paymentType);

            CustomerProfileType customerProfile = new CustomerProfileType();
            customerProfile.setMerchantCustomerId("M_" + email);
            customerProfile.setDescription("Profile for " + email);
            customerProfile.setEmail(email);
            customerProfile.getPaymentProfiles().add(paymentProfile);

            CreateCustomerProfileRequest apiRequest = new CreateCustomerProfileRequest();
            apiRequest.setMerchantAuthentication(merchantAuth());
            apiRequest.setProfile(customerProfile);
            apiRequest.setValidationMode(ValidationModeEnum.TEST_MODE);

            CreateCustomerProfileController controller = new CreateCustomerProfileController(apiRequest);
            controller.execute();
            CreateCustomerProfileResponse response = controller.getApiResponse();

            if (response == null) {
                throw new AuthorizeNetException(500, "No response from Authorize.net");
            }
            if (response.getMessages().getResultCode() != MessageTypeEnum.OK) {
                String msg = response.getMessages().getMessage().get(0).getText();
                throw new AuthorizeNetException(400, "Profile creation failed: " + msg);
            }

            String customerProfileId = response.getCustomerProfileId();
            String paymentProfileId = response.getCustomerPaymentProfileIdList()
                    .getNumericString().get(0);
            log.info("[correlationId={}] Created profile customerProfileId={}", MDC.get("correlationId"), customerProfileId);
            return new CustomerProfileResult(customerProfileId, paymentProfileId);

        } catch (AuthorizeNetException e) {
            throw e;
        } catch (Exception e) {
            log.error("[correlationId={}] createCustomerProfile failed: {}", MDC.get("correlationId"), e.getMessage(), e);
            throw new AuthorizeNetException(500, "createCustomerProfile error: " + e.getMessage());
        }
    }

    @CircuitBreaker(name = "gatewayCircuitBreaker")
    @Retry(name = "gatewayRetry")
    public TransactionResult chargeCustomerProfile(String customerProfileId,
                                                    String paymentProfileId,
                                                    BigDecimal amount,
                                                    String idempotencyKey) {
        log.info("[correlationId={}] Charging profile={} amount={} idempotencyKey={}",
                MDC.get("correlationId"), customerProfileId, amount, idempotencyKey);

        String idemKey = RedisKeys.idempotency(idempotencyKey);
        String cached = redisTemplate.opsForValue().get(idemKey);
        if (cached != null) {
            log.info("[correlationId={}] Idempotent hit for key={}", MDC.get("correlationId"), idempotencyKey);
            String[] parts = cached.split("\\|");
            return new TransactionResult(parts[0], parts[1], Integer.parseInt(parts[2]));
        }

        try {
            CustomerProfilePaymentType profileToCharge = new CustomerProfilePaymentType();
            profileToCharge.setCustomerProfileId(customerProfileId);

            PaymentProfile paymentProfile = new PaymentProfile();
            paymentProfile.setPaymentProfileId(paymentProfileId);
            profileToCharge.setPaymentProfile(paymentProfile);

            TransactionRequestType txnRequest = new TransactionRequestType();
            txnRequest.setTransactionType(TransactionTypeEnum.AUTH_CAPTURE_TRANSACTION.value());
            txnRequest.setProfile(profileToCharge);
            txnRequest.setAmount(amount.setScale(2, RoundingMode.CEILING));

            CreateTransactionRequest apiRequest = new CreateTransactionRequest();
            apiRequest.setMerchantAuthentication(merchantAuth());
            apiRequest.setTransactionRequest(txnRequest);

            CreateTransactionController controller = new CreateTransactionController(apiRequest);
            controller.execute();
            CreateTransactionResponse response = controller.getApiResponse();

            if (response == null) {
                throw new AuthorizeNetException(500, "No response from Authorize.net");
            }
            TransactionResponse txnResponse = response.getTransactionResponse();
            if (txnResponse == null) {
                String msg = response.getMessages().getMessage().get(0).getText();
                throw new AuthorizeNetException(400, "Charge failed: " + msg);
            }

            int responseCode = Integer.parseInt(txnResponse.getResponseCode());
            if (responseCode != 1) {
                String errorMsg = txnResponse.getErrors() != null && !txnResponse.getErrors().getError().isEmpty()
                        ? txnResponse.getErrors().getError().get(0).getErrorText()
                        : "Declined";
                throw new AuthorizeNetException(responseCode, "Charge declined: " + errorMsg);
            }

            TransactionResult result = new TransactionResult(
                    txnResponse.getTransId(), txnResponse.getAuthCode(), responseCode);

            redisTemplate.opsForValue().set(idemKey,
                    result.transactionId() + "|" + result.authCode() + "|" + result.responseCode(),
                    Duration.ofHours(24));

            log.info("[correlationId={}] Charge approved transactionId={}", MDC.get("correlationId"), result.transactionId());
            return result;

        } catch (AuthorizeNetException e) {
            throw e;
        } catch (Exception e) {
            log.error("[correlationId={}] chargeCustomerProfile failed: {}", MDC.get("correlationId"), e.getMessage(), e);
            throw new AuthorizeNetException(500, "chargeCustomerProfile error: " + e.getMessage());
        }
    }

    @CircuitBreaker(name = "gatewayCircuitBreaker")
    @Retry(name = "gatewayRetry")
    public TransactionResult refundTransaction(String transactionId, BigDecimal amount,
                                                String cardLast4, String cardExpiry) {
        log.info("[correlationId={}] Refunding transactionId={} amount={}", MDC.get("correlationId"), transactionId, amount);
        try {
            CreditCardType creditCard = new CreditCardType();
            creditCard.setCardNumber(cardLast4);
            creditCard.setExpirationDate(cardExpiry);

            PaymentType paymentType = new PaymentType();
            paymentType.setCreditCard(creditCard);

            TransactionRequestType txnRequest = new TransactionRequestType();
            txnRequest.setTransactionType(TransactionTypeEnum.REFUND_TRANSACTION.value());
            txnRequest.setRefTransId(transactionId);
            txnRequest.setAmount(amount.setScale(2, RoundingMode.CEILING));
            txnRequest.setPayment(paymentType);

            CreateTransactionRequest apiRequest = new CreateTransactionRequest();
            apiRequest.setMerchantAuthentication(merchantAuth());
            apiRequest.setTransactionRequest(txnRequest);

            CreateTransactionController controller = new CreateTransactionController(apiRequest);
            controller.execute();
            CreateTransactionResponse response = controller.getApiResponse();

            return extractTransactionResult(response, "Refund");

        } catch (AuthorizeNetException e) {
            throw e;
        } catch (Exception e) {
            log.error("[correlationId={}] refundTransaction failed: {}", MDC.get("correlationId"), e.getMessage(), e);
            throw new AuthorizeNetException(500, "refundTransaction error: " + e.getMessage());
        }
    }

    @CircuitBreaker(name = "gatewayCircuitBreaker")
    @Retry(name = "gatewayRetry")
    public TransactionResult voidTransaction(String transactionId) {
        log.info("[correlationId={}] Voiding transactionId={}", MDC.get("correlationId"), transactionId);
        try {
            TransactionRequestType txnRequest = new TransactionRequestType();
            txnRequest.setTransactionType(TransactionTypeEnum.VOID_TRANSACTION.value());
            txnRequest.setRefTransId(transactionId);

            CreateTransactionRequest apiRequest = new CreateTransactionRequest();
            apiRequest.setMerchantAuthentication(merchantAuth());
            apiRequest.setTransactionRequest(txnRequest);

            CreateTransactionController controller = new CreateTransactionController(apiRequest);
            controller.execute();
            CreateTransactionResponse response = controller.getApiResponse();

            return extractTransactionResult(response, "Void");

        } catch (AuthorizeNetException e) {
            throw e;
        } catch (Exception e) {
            log.error("[correlationId={}] voidTransaction failed: {}", MDC.get("correlationId"), e.getMessage(), e);
            throw new AuthorizeNetException(500, "voidTransaction error: " + e.getMessage());
        }
    }

    @CircuitBreaker(name = "gatewayCircuitBreaker")
    @Retry(name = "gatewayRetry")
    public void deleteCustomerProfile(String customerProfileId) {
        log.info("[correlationId={}] Deleting profile customerProfileId={}", MDC.get("correlationId"), customerProfileId);
        try {
            DeleteCustomerProfileRequest apiRequest = new DeleteCustomerProfileRequest();
            apiRequest.setMerchantAuthentication(merchantAuth());
            apiRequest.setCustomerProfileId(customerProfileId);

            DeleteCustomerProfileController controller = new DeleteCustomerProfileController(apiRequest);
            controller.execute();
            DeleteCustomerProfileResponse response = controller.getApiResponse();

            if (response == null) {
                throw new AuthorizeNetException(500, "No response from Authorize.net");
            }
            if (response.getMessages().getResultCode() != MessageTypeEnum.OK) {
                String msg = response.getMessages().getMessage().get(0).getText();
                throw new AuthorizeNetException(400, "Delete profile failed: " + msg);
            }
            log.info("[correlationId={}] Deleted profile customerProfileId={}", MDC.get("correlationId"), customerProfileId);

        } catch (AuthorizeNetException e) {
            throw e;
        } catch (Exception e) {
            log.error("[correlationId={}] deleteCustomerProfile failed: {}", MDC.get("correlationId"), e.getMessage(), e);
            throw new AuthorizeNetException(500, "deleteCustomerProfile error: " + e.getMessage());
        }
    }

    private TransactionResult extractTransactionResult(CreateTransactionResponse response, String operation) {
        if (response == null) {
            throw new AuthorizeNetException(500, "No response from Authorize.net");
        }
        TransactionResponse txnResponse = response.getTransactionResponse();
        if (txnResponse == null) {
            String msg = response.getMessages().getMessage().get(0).getText();
            throw new AuthorizeNetException(400, operation + " failed: " + msg);
        }
        int responseCode = Integer.parseInt(txnResponse.getResponseCode());
        if (responseCode != 1) {
            String errorMsg = txnResponse.getErrors() != null && !txnResponse.getErrors().getError().isEmpty()
                    ? txnResponse.getErrors().getError().get(0).getErrorText()
                    : "Failed";
            throw new AuthorizeNetException(responseCode, operation + " failed: " + errorMsg);
        }
        return new TransactionResult(txnResponse.getTransId(), txnResponse.getAuthCode(), responseCode);
    }
}
