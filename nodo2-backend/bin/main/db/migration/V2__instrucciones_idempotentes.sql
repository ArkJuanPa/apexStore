ALTER TABLE transacciones_pago
    ADD COLUMN respuesta_instrucciones JSONB NOT NULL DEFAULT '{}'::jsonb;
