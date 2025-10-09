# instant-payment-service

## Scope and Assumptions

This implementation targets **CHF instant payments** aligned with Alpian’s domestic operations.

- **Currency:** CHF only — no foreign exchange (FX) conversion.
- **Clearing:** Internal transfers between Alpian accounts only.  
  No external SIC, SEPA, or SWIFT connectivity in this version.
- **Limits:** No per-transaction or daily limits enforced for this technical assessment.
- **Identifiers:** Swiss IBANs (CH…) used for both debtor and creditor accounts.
- **Future readiness:** The architecture is structured to allow later integration with the SIC 5 clearing network via an external adapter.