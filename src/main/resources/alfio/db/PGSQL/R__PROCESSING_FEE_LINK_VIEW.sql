drop view if exists processing_fee_link_view;

create view processing_fee_link_view as (
    select pf.id id, pf.event_id_fk event_id_fk, pf.organization_id_fk organization_id_fk, pf.valid_from valid_from, pf.valid_to valid_to,
           pf.amount amount, pf.fee_type fee_type, pf.categories categories, pl.payment_method payment_method
    from processing_fee pf, processing_fee_link pl
        where pl.fee_id_fk = pf.id
);