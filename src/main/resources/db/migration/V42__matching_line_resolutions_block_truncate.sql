-- PROB-081 (suite) : V41 posait un trigger BEFORE UPDATE OR DELETE FOR EACH ROW, qui ne
-- couvre PAS TRUNCATE (commande au niveau instruction, qui contourne les triggers row-level).
-- Un TRUNCATE pourrait donc effacer toute la piste d'audit des resolutions sans etre bloque.
-- On ajoute un trigger BEFORE TRUNCATE FOR EACH STATEMENT pour fermer ce contournement.
-- La fonction reject_matching_resolution_mutation() leve deja sur n'importe quel TG_OP.

CREATE TRIGGER trg_matching_resolution_block_truncate
    BEFORE TRUNCATE ON three_way_matching_line_resolutions
    FOR EACH STATEMENT
EXECUTE FUNCTION reject_matching_resolution_mutation();
