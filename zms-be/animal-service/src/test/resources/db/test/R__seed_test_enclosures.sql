INSERT INTO enclosures (id, name, habitat)
VALUES
    ('550e8400-e29b-41d4-a716-446655440000', 'Test Savanna Enclosure', 'TERRESTRIAL'),
    ('660e8400-e29b-41d4-a716-446655440001', 'Test Aquatic Enclosure', 'AQUATIC')
ON CONFLICT (id) DO NOTHING;
