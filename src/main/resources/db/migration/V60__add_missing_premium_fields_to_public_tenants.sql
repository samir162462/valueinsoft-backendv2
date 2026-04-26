ALTER TABLE public.public_tenants ADD COLUMN IF NOT EXISTS cover_image_url VARCHAR(2048);
ALTER TABLE public.public_tenants ADD COLUMN IF NOT EXISTS description TEXT;
ALTER TABLE public.public_tenants ADD COLUMN IF NOT EXISTS working_hours TEXT;
