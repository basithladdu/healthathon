# SVK's specs (shared by Sujay H S, 29 Oct 2026)

- `Saanthvana_Clinical_Specification.docx` (+ `.txt` copy): the clinical rules. It covers who is eligible and what each field means, the V1–V9 and C1–C8 consistency rules, the ED view order, the board pathway and the sign-off checklist.
- `Advance_Medical_Directive_Platform_Tech_Spec.docx` (+ `.txt` copy): the build spec. It defines two linked records (ECTPR plan + AMD living will), the L1–L10 legal rules, the data model, the ED Quick View in 20 seconds, the QR wallet card, the Vidhi-format AMD PDF, and the scope to have ready for the hackathon.

## Fit with what is already built (prototype/app)

Already there: `ectpr.tsx` has the ED Quick View, the "If in doubt, resuscitate" box, "Not recorded" for blank fields, the CPR-vs-ceiling warning (V2), the review-due date and an offline QR wallet card. `tep-matrix.tsx` has the ceiling table.

Not built yet (spec "Must"):
- ECTPR entry wizard with the V1–V9 rules. V4 (understanding gate) and V5 (surrogate declaration) are new steps.
- Status lifecycle: Draft → Signed/Active → Out of date / Superseded / Revoked. Only one Active version.
- AMD questions → Vidhi-format PDF with blank signature, witness and notary blocks. Under-18 block.
- Telugu (we have English + Hindi) and an append-only audit log.

Differs from the spec:
- Our QR carries the plan text so it works offline. The spec wants a random, revocable token with no personal data. Decide: token + cached offline copy.
- The app is called **Sahara**. "Saanthvana" (our old name) and "Sahaara" in these docs both mean Sahara; the files are left as SVK sent them.
