-- V2: storage por claves, locks de tareas programadas y refresh token en cookie.

-- Antes lo hacía QuoteSchemaFix en cada arranque: ddl-auto había creado un CHECK con los
-- estados viejos de presupuesto y guardar PAID fallaba.
ALTER TABLE quotes DROP CONSTRAINT IF EXISTS quotes_status_check;

-- El PDF se genera en cada pedido; la copia en disco ya no existe.
ALTER TABLE quotes DROP COLUMN IF EXISTS pdf_path;

-- Fotos: se guarda la clave del storage, no la URL. Las URLs absolutas viejas
-- ("http://host/uploads/orders/<uuid>.jpg") pasan a "orders/<uuid>.jpg".
ALTER TABLE order_photos RENAME COLUMN url TO storage_key;
UPDATE order_photos
SET storage_key = regexp_replace(storage_key, '^.*/uploads/(orders|printers)/', '\1/')
WHERE storage_key ~ '/uploads/(orders|printers)/';

ALTER TABLE printers RENAME COLUMN photo_url TO photo_key;
UPDATE printers
SET photo_key = regexp_replace(photo_key, '^.*/uploads/(orders|printers)/', '\1/')
WHERE photo_key ~ '/uploads/(orders|printers)/';

-- ShedLock: una sola réplica ejecuta cada tarea programada.
CREATE TABLE shedlock (
    name       VARCHAR(64)  NOT NULL PRIMARY KEY,
    lock_until TIMESTAMP    NOT NULL,
    locked_at  TIMESTAMP    NOT NULL,
    locked_by  VARCHAR(255) NOT NULL
);

-- La cookie del refresh token es persistente ("recordarme") o de sesión.
ALTER TABLE refresh_tokens ADD COLUMN remember_me BOOLEAN NOT NULL DEFAULT FALSE;
