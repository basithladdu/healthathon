export type CareArtKind =
  | 'cancer-overview'
  | 'symptom-diary'
  | 'care-story'
  | 'doctor-pack'
  | 'home-help'
  | 'support-places'
  | 'open-questions'
  | 'copy-tracker'
  | 'voice-journal'
  | 'cost-help'
  | 'lab-history';

export function CareArt({ kind, className }: { kind: CareArtKind; className?: string }) {
  return <svg className={className} viewBox="0 0 120 100" width="120" height="100" fill="none" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" focusable="false">
    <ellipse cx="61" cy="88" rx="38" ry="6" fill="#6D7662" opacity=".12" />
    {kind === 'cancer-overview' && <g>
      <circle cx="88" cy="24" r="14" fill="#F3C153" />
      <path d="M89 76c12-6 17-17 14-27-10 2-17 12-14 27Z" fill="#74A89B" />
      <path d="M91 78c8-2 13-6 15-12-8-3-14 2-15 12Z" fill="#278C80" />
      <g transform="rotate(-7 55 48)">
        <rect x="26" y="16" width="64" height="68" rx="12" fill="#216B67" />
        <rect x="21" y="12" width="64" height="68" rx="12" fill="#35A398" />
        <rect x="29" y="19" width="49" height="54" rx="7" fill="#FFF5E3" />
        <path d="M53 57C46 52 35 44 35 36c0-9 12-13 18-5 6-8 18-4 18 5 0 8-11 16-18 21Z" fill="#DD7555" />
        <path d="M40 36c0-3 3-5 6-4" stroke="#F7B79D" strokeWidth="3" />
        <path d="M43 64h20" stroke="#BDD9CA" strokeWidth="4" />
      </g>
      <path d="M17 66c-7-4-10-10-8-16 7 1 11 8 8 16Z" fill="#ADA1D2" />
    </g>}
    {kind === 'symptom-diary' && <g>
      <g transform="rotate(-8 47 51)">
        <rect x="22" y="17" width="51" height="67" rx="9" fill="#4B7FAD" />
        <rect x="20" y="13" width="49" height="67" rx="9" fill="#83B2D1" />
        <path d="M30 14v65" stroke="#427B9E" strokeWidth="5" />
        <path d="M46 57c-5-4-15-11-15-18 0-7 10-10 15-4 5-6 15-3 15 4 0 7-10 14-15 18Z" fill="#FFF5E3" />
        <path d="M40 66h15" stroke="#C6E1E5" strokeWidth="4" />
      </g>
      <g transform="rotate(30 87 58)">
        <rect x="74" y="32" width="25" height="51" rx="12.5" fill="#CB694F" />
        <rect x="71" y="29" width="25" height="51" rx="12.5" fill="#F7CD68" />
        <path d="M71 54h25v13.5a12.5 12.5 0 0 1-25 0V54Z" fill="#E58765" />
        <path d="M77 46v-5a7 7 0 0 1 7-7" stroke="#FFE9AC" strokeWidth="3" />
        <path d="M77 59v7" stroke="#F6B9A0" strokeWidth="3" />
      </g>
    </g>}
    {kind === 'care-story' && <g>
      <path d="M16 27c16-5 30-2 44 7 14-9 28-12 44-7v53c-17-5-31-3-44 4-13-7-27-9-44-4V27Z" fill="#8574B0" />
      <path d="M20 20c15-3 28 0 40 8 12-8 25-11 40-8v53c-15-3-28 0-40 8-12-8-25-11-40-8V20Z" fill="#FAEBDD" />
      <path d="M60 28c12-8 25-11 40-8v53c-15-3-28 0-40 8V28Z" fill="#FFF8EC" />
      <path d="M60 28v53" stroke="#CCB6C9" strokeWidth="2" />
      <path d="M29 37c8 0 14 2 22 5M29 49c8 0 14 2 22 5M29 61c6 0 11 1 16 3" stroke="#C9B6C6" strokeWidth="3" />
      <path d="M72 39c10 1 14 6 13 12s-7 9-10 14" stroke="#94BDB0" strokeWidth="3" />
      <circle cx="72" cy="38" r="5" fill="#E18A68" />
      <circle cx="85" cy="52" r="5" fill="#3A9B8F" />
      <circle cx="75" cy="65" r="5" fill="#E9B64E" />
      <path d="M84 18v15l5-4 5 3V17" fill="#DCA851" />
    </g>}
    {kind === 'doctor-pack' && <g>
      <path d="M44 43V28a8 8 0 0 1 8-8h17a8 8 0 0 1 8 8v15" stroke="#427F92" strokeWidth="7" />
      <g transform="rotate(7 69 41)">
        <path d="M47 13h30l14 14v43H47V13Z" fill="#B1A1D2" />
        <path d="M43 10h30l14 14v43H43V10Z" fill="#FFF5E3" />
        <path d="M73 10v14h14" fill="#F4C565" />
        <path d="M52 34h25M52 43h18" stroke="#B9D2CB" strokeWidth="4" />
      </g>
      <rect x="18" y="39" width="85" height="46" rx="11" fill="#276C78" />
      <path d="M18 49a10 10 0 0 1 10-10h65a10 10 0 0 1 10 10v8H18v-8Z" fill="#579DAB" />
      <rect x="27" y="54" width="67" height="27" rx="7" fill="#3A8894" />
      <path d="M56 47h10v18a5 5 0 0 1-10 0V47Z" fill="#EAA66C" />
      <path d="M29 44h17" stroke="#8EC0C5" strokeWidth="3" />
    </g>}
    {kind === 'home-help' && <g>
      <path d="M91 77V38L61 18 27 45v34a6 6 0 0 0 6 6h52a6 6 0 0 0 6-6v-2Z" fill="#DCB889" />
      <path d="M26 42 58 17l31 25v35a5 5 0 0 1-5 5H31a5 5 0 0 1-5-5V42Z" fill="#FFE0A1" />
      <path d="m17 42 38-30a5 5 0 0 1 6 0l38 30-8 9-33-26-33 26-8-9Z" fill="#D87353" />
      <path d="m26 39 32-25 32 25" stroke="#F0AA7D" strokeWidth="3" />
      <path d="M51 82V61a10 10 0 0 1 20 0v21" fill="#388E81" />
      <rect x="34" y="47" width="13" height="13" rx="3" fill="#7DAFC9" />
      <path d="M38 50h5" stroke="#C5E3E5" strokeWidth="2" />
      <circle cx="64" cy="67" r="2" fill="#FFE3A2" />
      <path d="M95 80V64" stroke="#518778" strokeWidth="3" />
      <path d="M95 71c-10-1-15-8-12-15 10 0 14 6 12 15Z" fill="#9C8ABC" />
      <path d="M95 64c10-2 14-8 10-14-8 1-12 7-10 14Z" fill="#6CA995" />
      <path d="M86 77h18l-3 9H89l-3-9Z" fill="#CE896D" />
    </g>}
    {kind === 'support-places' && <g>
      <path d="m16 37 29-9 29 10 30-10v50L75 88 45 78 16 88V37Z" fill="#608EA0" />
      <path d="m14 31 29-9 30 10 30-10v51L73 83 43 73 14 83V31Z" fill="#A7D1C9" />
      <path d="m43 22 30 10v51L43 73V22Z" fill="#FFF0D5" />
      <path d="m73 32 30-10v51L73 83V32Z" fill="#B0AAD6" />
      <path d="m17 61 26-13 30 11 28-14M34 26l-3 23 10 18-1 7M88 29l-4 22 11 18" stroke="#FFFFFF" strokeWidth="4" opacity=".7" />
      <path d="M63 12c-13 0-22 9-22 21 0 16 22 34 22 34s22-18 22-34c0-12-9-21-22-21Z" fill="#B6604B" />
      <path d="M59 8c-13 0-22 9-22 21 0 16 22 34 22 34s22-18 22-34c0-12-9-21-22-21Z" fill="#E48761" />
      <circle cx="59" cy="29" r="9" fill="#FFF1D5" />
      <path d="M45 27c0-7 6-13 13-13" stroke="#F5B596" strokeWidth="3" />
    </g>}
    {kind === 'open-questions' && <g>
      <path d="M51 18h39a15 15 0 0 1 15 15v14a15 15 0 0 1-15 15h-3l4 12-19-12H51a15 15 0 0 1-15-15V33a15 15 0 0 1 15-15Z" fill="#8F7CB3" />
      <path d="M48 14h39a15 15 0 0 1 15 15v14a15 15 0 0 1-15 15h-3l4 12-19-12H48a15 15 0 0 1-15-15V29a15 15 0 0 1 15-15Z" fill="#B5A3D7" />
      <path d="M57 28h28M69 38h16" stroke="#E8DFF5" strokeWidth="4" />
      <path d="M29 39h37a15 15 0 0 1 15 15v15a15 15 0 0 1-15 15H44L25 94l3-10a15 15 0 0 1-14-15V54a15 15 0 0 1 15-15Z" fill="#246E68" />
      <path d="M26 35h37a15 15 0 0 1 15 15v15a15 15 0 0 1-15 15H41L22 90l3-10a15 15 0 0 1-14-15V50a15 15 0 0 1 15-15Z" fill="#379B8D" />
      <circle cx="30" cy="57" r="4" fill="#FFF1D7" />
      <circle cx="45" cy="57" r="4" fill="#FFF1D7" />
      <circle cx="60" cy="57" r="4" fill="#FFF1D7" />
      <circle cx="17" cy="21" r="6" fill="#F3C15D" />
    </g>}
    {kind === 'copy-tracker' && <g>
      <g transform="rotate(-9 43 44)">
        <path d="M22 15h30l13 13v46H22V15Z" fill="#8777AE" />
        <path d="M18 11h30l13 13v46H18V11Z" fill="#B5A4D4" />
        <path d="M48 11v13h13" fill="#DFD2EA" />
        <rect x="27" y="34" width="23" height="4" rx="2" fill="#F4EAF5" />
        <rect x="27" y="44" width="16" height="4" rx="2" fill="#F4EAF5" />
      </g>
      <g transform="rotate(8 80 55)">
        <path d="M59 26h30l13 13v46H59V26Z" fill="#CCA785" />
        <path d="M55 22h30l13 13v46H55V22Z" fill="#FFF1D8" />
        <path d="M85 22v13h13" fill="#EAA26E" />
        <rect x="65" y="43" width="23" height="4" rx="2" fill="#DCBA98" />
        <rect x="65" y="53" width="16" height="4" rx="2" fill="#DCBA98" />
      </g>
      <path d="M29 68c0 10 10 15 23 14 9-1 15-4 20-10" stroke="#FFF5E4" strokeWidth="11" />
      <path d="M29 68c0 10 10 15 23 14 9-1 15-4 20-10" stroke="#308F82" strokeWidth="6" />
      <path d="m63 70 11-3 1 12" stroke="#308F82" strokeWidth="6" />
    </g>}
    {kind === 'voice-journal' && <g>
      <path d="M47 86h29" stroke="#5F739C" strokeWidth="7" />
      <path d="M61 72v13" stroke="#5F739C" strokeWidth="7" />
      <path d="M35 45v6a26 26 0 0 0 52 0v-6" stroke="#2B7B78" strokeWidth="7" />
      <rect x="45" y="12" width="34" height="54" rx="17" fill="#C97B56" />
      <rect x="41" y="8" width="34" height="54" rx="17" fill="#F4C564" />
      <path d="M41 39h34v6a17 17 0 0 1-34 0v-6Z" fill="#E99367" />
      <path d="M51 20h14M49 28h18" stroke="#FFF0BD" strokeWidth="3" />
      <path d="M51 47h14" stroke="#F5B491" strokeWidth="3" />
      <path d="M24 30c-5 7-5 14 0 21M15 22c-8 12-8 26 0 38" stroke="#B3A1D1" strokeWidth="4" />
      <path d="M96 30c5 7 5 14 0 21M105 22c8 12 8 26 0 38" stroke="#83B6CB" strokeWidth="4" />
    </g>}
    {kind === 'cost-help' && <g>
      <path d="M23 29a10 10 0 0 1 10-10h52v24H23V29Z" fill="#D37F58" />
      <path d="M33 23h48" stroke="#F0B084" strokeWidth="4" />
      <g transform="rotate(-12 52 31)">
        <path d="M42 14h28v30H42V14Z" fill="#CF9E42" />
        <path d="M39 11h28v30H39V11Z" fill="#F6CE6F" />
        <path d="M46 17h14v17H46V17Z" stroke="#E8AF4C" strokeWidth="3" />
      </g>
      <rect x="18" y="32" width="84" height="53" rx="12" fill="#256B68" />
      <rect x="15" y="28" width="84" height="53" rx="12" fill="#35958A" />
      <path d="M25 36h48M25 72h47" stroke="#73B5A4" strokeWidth="2" />
      <path d="M78 45h27v26H78a13 13 0 0 1 0-26Z" fill="#8873AB" />
      <path d="M75 41h27v26H75a13 13 0 0 1 0-26Z" fill="#B7A1D3" />
      <circle cx="77" cy="54" r="4" fill="#F8D785" />
      <ellipse cx="26" cy="85" rx="13" ry="4" fill="#D5A03D" />
      <path d="M13 78h26v7H13v-7Z" fill="#DDAA47" />
      <ellipse cx="26" cy="78" rx="13" ry="4" fill="#F7D577" />
    </g>}
    {kind === 'lab-history' && <g>
      <g transform="rotate(-14 45 48)">
        <path d="M30 20h24v48a12 12 0 0 1-24 0V20Z" fill="#71A4B8" />
        <path d="M27 17h24v48a12 12 0 0 1-24 0V17Z" fill="#E2F0E8" />
        <path d="M30 42c7-4 11 4 18 0v22a9 9 0 0 1-18 0V42Z" fill="#43A596" />
        <rect x="24" y="12" width="30" height="12" rx="4" fill="#7CADD0" />
        <path d="M33 28v8" stroke="#FFFFFF" strokeWidth="3" />
        <circle cx="37" cy="55" r="3" fill="#9CDEBF" />
      </g>
      <g transform="rotate(13 79 49)">
        <path d="M68 20h24v48a12 12 0 0 1-24 0V20Z" fill="#B39CBD" />
        <path d="M65 17h24v48a12 12 0 0 1-24 0V17Z" fill="#FAE8DD" />
        <path d="M68 51c7-4 11 4 18 0v13a9 9 0 0 1-18 0V51Z" fill="#E48A67" />
        <rect x="62" y="12" width="30" height="12" rx="4" fill="#B49AD1" />
        <path d="M71 28v16" stroke="#FFF8F0" strokeWidth="3" />
        <circle cx="79" cy="61" r="3" fill="#F8BF9A" />
      </g>
      <path d="M24 74v10h73V74" stroke="#C38B38" strokeWidth="7" />
      <path d="M22 72v10h73V72" stroke="#F0C360" strokeWidth="7" />
    </g>}
  </svg>;
}
