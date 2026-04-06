UPDATE public.users
SET
    "userPassword" = 'admin123',
    "userEmail" = 'admin@valueinsoft.local',
    "userRole" = 'SupportAdmin',
    "userPhone" = NULL,
    "branchId" = 0,
    "firstName" = 'Platform',
    "lastName" = 'Admin',
    gender = 0,
    "imgFile" = NULL
WHERE "userName" = 'admin';

INSERT INTO public.users (
    "userName",
    "userPassword",
    "userEmail",
    "userRole",
    "userPhone",
    "branchId",
    "firstName",
    "lastName",
    gender,
    "creationTime",
    "imgFile"
)
SELECT
    'admin',
    'admin123',
    'admin@valueinsoft.local',
    'SupportAdmin',
    NULL,
    0,
    'Platform',
    'Admin',
    0,
    NOW(),
    NULL
WHERE NOT EXISTS (
    SELECT 1
    FROM public.users
    WHERE "userName" = 'admin'
);
