INSERT INTO plans (id, code, name, price_monthly, price_annual, ai_enabled,
                   max_surveys_per_month, token_allowance, trial_days, active, created_at)
VALUES
    (gen_random_uuid(), 'FREE',                 'Free',                   0.00,   0.00,   false, 2,  0,     30, true, now()),
    (gen_random_uuid(), 'INDIVIDUAL_MONTHLY',   'Individual Monthly',     9.99,   0.00,   false, -1, 0,     0,  true, now()),
    (gen_random_uuid(), 'INDIVIDUAL_ANNUAL',    'Individual Annual',      0.00,   99.99,  false, -1, 0,     0,  true, now()),
    (gen_random_uuid(), 'INDIVIDUAL_AI_MONTHLY','Individual AI Monthly',  19.99,  0.00,   true,  -1, 1000,  0,  true, now()),
    (gen_random_uuid(), 'INDIVIDUAL_AI_ANNUAL', 'Individual AI Annual',   0.00,   199.99, true,  -1, 12000, 0,  true, now());
