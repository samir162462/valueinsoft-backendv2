INSERT INTO public.business_packages (
    package_id,
    display_name,
    onboarding_label,
    business_type,
    status,
    config_version,
    description,
    default_template_id,
    display_order,
    featured
) VALUES
    (
        'mobile_shop',
        'Mobile Shop',
        'Mobile shop',
        'retail_mobile',
        'active',
        'v1',
        'Mobile retail operations with devices, accessories, and service-friendly category defaults.',
        'single_branch_retail',
        10,
        TRUE
    ),
    (
        'car_workshop',
        'Car Workshop',
        'Car workshop',
        'workshop_service',
        'active',
        'v1',
        'Workshop-oriented package with service, parts, and accessories category defaults.',
        'service_office',
        20,
        FALSE
    )
ON CONFLICT (package_id) DO UPDATE
SET
    display_name = EXCLUDED.display_name,
    onboarding_label = EXCLUDED.onboarding_label,
    business_type = EXCLUDED.business_type,
    status = EXCLUDED.status,
    config_version = EXCLUDED.config_version,
    description = EXCLUDED.description,
    default_template_id = EXCLUDED.default_template_id,
    display_order = EXCLUDED.display_order,
    featured = EXCLUDED.featured,
    updated_at = NOW();

DELETE FROM public.business_package_groups WHERE package_id IN ('mobile_shop', 'car_workshop');

WITH inserted_groups AS (
    INSERT INTO public.business_package_groups (
        package_id,
        group_key,
        display_name,
        status,
        display_order
    ) VALUES
        ('mobile_shop', 'mobile_devices', 'Mobile devices', 'active', 10),
        ('mobile_shop', 'mobile_accessories', 'Mobile accessories', 'active', 20),
        ('mobile_shop', 'mobile_services', 'Mobile services', 'active', 30),
        ('car_workshop', 'workshop_services', 'Workshop services', 'active', 10),
        ('car_workshop', 'car_parts', 'Car parts', 'active', 20),
        ('car_workshop', 'car_accessories', 'Car accessories', 'active', 30)
    RETURNING group_id, package_id, group_key
),
inserted_categories AS (
    INSERT INTO public.business_package_categories (
        group_id,
        category_key,
        display_name,
        status,
        display_order
    )
    SELECT group_id, category_key, display_name, 'active', display_order
    FROM (
        SELECT ig.group_id, 'smartphones' AS category_key, 'Smartphones' AS display_name, 10 AS display_order
        FROM inserted_groups ig WHERE ig.package_id = 'mobile_shop' AND ig.group_key = 'mobile_devices'
        UNION ALL
        SELECT ig.group_id, 'tablets', 'Tablets', 20
        FROM inserted_groups ig WHERE ig.package_id = 'mobile_shop' AND ig.group_key = 'mobile_devices'
        UNION ALL
        SELECT ig.group_id, 'wearables', 'Wearables', 30
        FROM inserted_groups ig WHERE ig.package_id = 'mobile_shop' AND ig.group_key = 'mobile_devices'
        UNION ALL
        SELECT ig.group_id, 'accessories', 'Accessories', 10
        FROM inserted_groups ig WHERE ig.package_id = 'mobile_shop' AND ig.group_key = 'mobile_accessories'
        UNION ALL
        SELECT ig.group_id, 'spare_parts', 'Spare parts', 20
        FROM inserted_groups ig WHERE ig.package_id = 'mobile_shop' AND ig.group_key = 'mobile_accessories'
        UNION ALL
        SELECT ig.group_id, 'repair_services', 'Repair services', 10
        FROM inserted_groups ig WHERE ig.package_id = 'mobile_shop' AND ig.group_key = 'mobile_services'
        UNION ALL
        SELECT ig.group_id, 'software_services', 'Software services', 20
        FROM inserted_groups ig WHERE ig.package_id = 'mobile_shop' AND ig.group_key = 'mobile_services'
        UNION ALL
        SELECT ig.group_id, 'maintenance_services', 'Maintenance services', 10
        FROM inserted_groups ig WHERE ig.package_id = 'car_workshop' AND ig.group_key = 'workshop_services'
        UNION ALL
        SELECT ig.group_id, 'diagnostics', 'Diagnostics', 20
        FROM inserted_groups ig WHERE ig.package_id = 'car_workshop' AND ig.group_key = 'workshop_services'
        UNION ALL
        SELECT ig.group_id, 'replacement_parts', 'Replacement parts', 10
        FROM inserted_groups ig WHERE ig.package_id = 'car_workshop' AND ig.group_key = 'car_parts'
        UNION ALL
        SELECT ig.group_id, 'fluids_and_consumables', 'Fluids and consumables', 20
        FROM inserted_groups ig WHERE ig.package_id = 'car_workshop' AND ig.group_key = 'car_parts'
        UNION ALL
        SELECT ig.group_id, 'interior_accessories', 'Interior accessories', 10
        FROM inserted_groups ig WHERE ig.package_id = 'car_workshop' AND ig.group_key = 'car_accessories'
        UNION ALL
        SELECT ig.group_id, 'exterior_accessories', 'Exterior accessories', 20
        FROM inserted_groups ig WHERE ig.package_id = 'car_workshop' AND ig.group_key = 'car_accessories'
    ) category_seed
    RETURNING category_id, category_key, display_name
)
INSERT INTO public.business_package_subcategories (
    category_id,
    subcategory_key,
    display_name,
    status,
    display_order
)
SELECT category_id, subcategory_key, display_name, 'active', display_order
FROM (
    SELECT ic.category_id, 'android_phones' AS subcategory_key, 'Android phones' AS display_name, 10 AS display_order
    FROM inserted_categories ic WHERE ic.category_key = 'smartphones'
    UNION ALL
    SELECT ic.category_id, 'iphone', 'iPhone', 20
    FROM inserted_categories ic WHERE ic.category_key = 'smartphones'
    UNION ALL
    SELECT ic.category_id, 'feature_phones', 'Feature phones', 30
    FROM inserted_categories ic WHERE ic.category_key = 'smartphones'
    UNION ALL
    SELECT ic.category_id, 'android_tablets', 'Android tablets', 10
    FROM inserted_categories ic WHERE ic.category_key = 'tablets'
    UNION ALL
    SELECT ic.category_id, 'ipad', 'iPad', 20
    FROM inserted_categories ic WHERE ic.category_key = 'tablets'
    UNION ALL
    SELECT ic.category_id, 'smart_watches', 'Smart watches', 10
    FROM inserted_categories ic WHERE ic.category_key = 'wearables'
    UNION ALL
    SELECT ic.category_id, 'fitness_bands', 'Fitness bands', 20
    FROM inserted_categories ic WHERE ic.category_key = 'wearables'
    UNION ALL
    SELECT ic.category_id, 'chargers', 'Chargers', 10
    FROM inserted_categories ic WHERE ic.category_key = 'accessories'
    UNION ALL
    SELECT ic.category_id, 'cables', 'Cables', 20
    FROM inserted_categories ic WHERE ic.category_key = 'accessories'
    UNION ALL
    SELECT ic.category_id, 'cases', 'Cases', 30
    FROM inserted_categories ic WHERE ic.category_key = 'accessories'
    UNION ALL
    SELECT ic.category_id, 'screen_protectors', 'Screen protectors', 40
    FROM inserted_categories ic WHERE ic.category_key = 'accessories'
    UNION ALL
    SELECT ic.category_id, 'screens', 'Screens', 10
    FROM inserted_categories ic WHERE ic.category_key = 'spare_parts'
    UNION ALL
    SELECT ic.category_id, 'batteries', 'Batteries', 20
    FROM inserted_categories ic WHERE ic.category_key = 'spare_parts'
    UNION ALL
    SELECT ic.category_id, 'charging_ports', 'Charging ports', 30
    FROM inserted_categories ic WHERE ic.category_key = 'spare_parts'
    UNION ALL
    SELECT ic.category_id, 'screen_repair', 'Screen repair', 10
    FROM inserted_categories ic WHERE ic.category_key = 'repair_services'
    UNION ALL
    SELECT ic.category_id, 'battery_replacement', 'Battery replacement', 20
    FROM inserted_categories ic WHERE ic.category_key = 'repair_services'
    UNION ALL
    SELECT ic.category_id, 'software_unlock', 'Software unlock', 10
    FROM inserted_categories ic WHERE ic.category_key = 'software_services'
    UNION ALL
    SELECT ic.category_id, 'software_update', 'Software update', 20
    FROM inserted_categories ic WHERE ic.category_key = 'software_services'
    UNION ALL
    SELECT ic.category_id, 'oil_change', 'Oil change', 10
    FROM inserted_categories ic WHERE ic.category_key = 'maintenance_services'
    UNION ALL
    SELECT ic.category_id, 'brake_service', 'Brake service', 20
    FROM inserted_categories ic WHERE ic.category_key = 'maintenance_services'
    UNION ALL
    SELECT ic.category_id, 'engine_scan', 'Engine scan', 10
    FROM inserted_categories ic WHERE ic.category_key = 'diagnostics'
    UNION ALL
    SELECT ic.category_id, 'electrical_diagnostics', 'Electrical diagnostics', 20
    FROM inserted_categories ic WHERE ic.category_key = 'diagnostics'
    UNION ALL
    SELECT ic.category_id, 'filters', 'Filters', 10
    FROM inserted_categories ic WHERE ic.category_key = 'replacement_parts'
    UNION ALL
    SELECT ic.category_id, 'brake_pads', 'Brake pads', 20
    FROM inserted_categories ic WHERE ic.category_key = 'replacement_parts'
    UNION ALL
    SELECT ic.category_id, 'belts', 'Belts', 30
    FROM inserted_categories ic WHERE ic.category_key = 'replacement_parts'
    UNION ALL
    SELECT ic.category_id, 'engine_oil', 'Engine oil', 10
    FROM inserted_categories ic WHERE ic.category_key = 'fluids_and_consumables'
    UNION ALL
    SELECT ic.category_id, 'coolant', 'Coolant', 20
    FROM inserted_categories ic WHERE ic.category_key = 'fluids_and_consumables'
    UNION ALL
    SELECT ic.category_id, 'seat_covers', 'Seat covers', 10
    FROM inserted_categories ic WHERE ic.category_key = 'interior_accessories'
    UNION ALL
    SELECT ic.category_id, 'floor_mats', 'Floor mats', 20
    FROM inserted_categories ic WHERE ic.category_key = 'interior_accessories'
    UNION ALL
    SELECT ic.category_id, 'lights', 'Lights', 10
    FROM inserted_categories ic WHERE ic.category_key = 'exterior_accessories'
    UNION ALL
    SELECT ic.category_id, 'mirrors', 'Mirrors', 20
    FROM inserted_categories ic WHERE ic.category_key = 'exterior_accessories'
) subcategory_seed;
