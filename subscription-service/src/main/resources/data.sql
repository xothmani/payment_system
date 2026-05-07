INSERT INTO plans (id, code, name, price_monthly, price_annual, ai_enabled,
                   max_surveys_per_month, token_allowance, trial_days, paddle_price_id, active, created_at)
VALUES
    (gen_random_uuid(), 'FREE',                 'Free',                   0.00,   0.00,   false, 2,  0,     30, null,                              true, now()),
    (gen_random_uuid(), 'INDIVIDUAL_MONTHLY',   'Individual Monthly',     9.99,   0.00,   false, -1, 0,     0,  'pri_01kqygmqdz5ccsw3k6kbmwvng1', true, now()),
    (gen_random_uuid(), 'INDIVIDUAL_ANNUAL',    'Individual Annual',      0.00,   99.99,  false, -1, 0,     0,  'pri_01kqygs0gfff3cqstx9z86nmdm', true, now()),
    (gen_random_uuid(), 'INDIVIDUAL_AI_MONTHLY','Individual AI Monthly',  19.99,  0.00,   true,  -1, 1000,  0,  'pri_01kqygr2c83f7e2r050xn6n0ap', true, now()),
    (gen_random_uuid(), 'INDIVIDUAL_AI_ANNUAL', 'Individual AI Annual',   0.00,   199.99, true,  -1, 12000, 0,  'pri_01kqygvj1nrwk8wx707f35ascn', true, now());
