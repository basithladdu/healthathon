// DPDP notice content and the "download my data" bundle (tech spec §7, L10). DPDP Act 2023 + Rules 2025, duties from May 2027.
// Written for a Class 8 reading level. It says only what the code does: see the data flows in ed-card-client.ts and api/ed-card/route.ts.
import type { AmdVersion, AuditEvent, EctprDistribution, EctprRecord, EpisodeLog, IncidentReport, OutcomeRecord } from './ectpr-model.ts';

export type DpdpScope = 'care-plan' | 'living-will';
export type NoticeBlock = { titleEn: string; titleHi: string; en: string; hi: string };

export function dpdpNotice(scope: DpdpScope): NoticeBlock[] {
  const stored: NoticeBlock = scope === 'care-plan' ? {
    titleEn: 'What we keep', titleHi: 'हम क्या रखते हैं',
    en: 'Name, hospital number, date of birth, address and phone. The people who decide for the patient. Illness details, treatment choices, the patient’s own words and signatures. A log of who opened or changed the plan.',
    hi: 'नाम, अस्पताल नंबर, जन्म तिथि, पता और फ़ोन। मरीज़ की ओर से फ़ैसला लेने वाले लोग। बीमारी की जानकारी, इलाज के फ़ैसले, मरीज़ के अपने शब्द और हस्ताक्षर। यह रिकॉर्ड कि किसने योजना खोली या बदली।',
  } : {
    titleEn: 'What we keep', titleHi: 'हम क्या रखते हैं',
    en: 'Your name, date of birth, address and the last 4 digits of your ID. Your representatives and witnesses. Your treatment wishes. The signed scan and its fingerprint (SHA-256). A log of each change, print and share.',
    hi: 'आपका नाम, जन्म तिथि, पता और पहचान पत्र के आख़िरी 4 अंक। आपके प्रतिनिधि और गवाह। इलाज के बारे में आपकी इच्छाएँ। हस्ताक्षर वाला स्कैन और उसकी पहचान (SHA-256)। हर बदलाव, प्रिंट और साझा करने का रिकॉर्ड।',
  };
  const why: NoticeBlock = scope === 'care-plan' ? {
    titleEn: 'Why', titleHi: 'क्यों',
    en: 'So that an emergency doctor can find and follow the patient’s wishes, and the care team can review them.',
    hi: 'ताकि इमरजेंसी डॉक्टर मरीज़ की इच्छाएँ देख और मान सकें, और देखभाल टीम उनकी समीक्षा कर सके।',
  } : {
    titleEn: 'Why', titleHi: 'क्यों',
    en: 'To make your living will paper, keep track of its versions, and show doctors that it exists.',
    hi: 'आपकी लिविंग विल का काग़ज़ बनाने, उसके संस्करण सँभालने और डॉक्टरों को यह दिखाने के लिए कि वह मौजूद है।',
  };
  const where: NoticeBlock = scope === 'care-plan' ? {
    titleEn: 'Where it is', titleHi: 'यह कहाँ रहता है',
    en: 'On this device, in this browser. It leaves this device only in two cases. If an emergency card is issued, a copy of the signed plan goes to the Sahara server so the QR code works. If an SMS is sent, the phone number goes to the SMS company.',
    hi: 'इसी डिवाइस पर, इसी ब्राउज़र में। यह सिर्फ़ दो हालात में बाहर जाता है। इमरजेंसी कार्ड जारी होने पर साइन की गई योजना की कॉपी Sahara सर्वर पर जाती है, ताकि QR कोड चले। SMS भेजने पर फ़ोन नंबर SMS कंपनी के पास जाता है।',
  } : {
    titleEn: 'Where it is', titleHi: 'यह कहाँ रहता है',
    en: 'On this device, in this browser. It is not sent to any server. If an emergency card is issued, the card carries only the living will’s status, date, version and attesting officer. You decide who gets a paper copy.',
    hi: 'इसी डिवाइस पर, इसी ब्राउज़र में। यह किसी सर्वर पर नहीं भेजा जाता। इमरजेंसी कार्ड जारी होने पर कार्ड में सिर्फ़ लिविंग विल की स्थिति, तारीख़, संस्करण और सत्यापन अधिकारी रहते हैं। काग़ज़ की कॉपी किसे देनी है, यह आप तय करते हैं।',
  };
  const rights: NoticeBlock = scope === 'care-plan' ? {
    titleEn: 'Your rights', titleHi: 'आपके अधिकार',
    en: 'See it: download your data below. Correct it: ask for a review, and a new version is made. Withdraw: cancel the plan at any time, and the card stops working. Delete: remove an unsigned draft below. Signed versions and the log cannot be deleted, because they show what was decided and who saw it.',
    hi: 'देखना: नीचे अपना डेटा डाउनलोड करें। सुधारना: समीक्षा माँगें, नया संस्करण बनेगा। वापस लेना: योजना कभी भी रद्द करें, कार्ड काम करना बंद कर देगा। मिटाना: बिना साइन का ड्राफ़्ट नीचे से हटाएँ। साइन किए गए संस्करण और रिकॉर्ड नहीं मिटते, क्योंकि उनसे पता चलता है कि क्या तय हुआ और किसने देखा।',
  } : {
    titleEn: 'Your rights', titleHi: 'आपके अधिकार',
    en: 'See it: download your data below. Correct it: make a new version. Withdraw: cancel it in writing at any time. Delete: remove an unsigned draft below. Signed versions and the log cannot be deleted. Deleting here does not touch any paper you printed, so destroy that yourself.',
    hi: 'देखना: नीचे अपना डेटा डाउनलोड करें। सुधारना: नया संस्करण बनाएँ। वापस लेना: कभी भी लिखित रूप में रद्द करें। मिटाना: बिना साइन का ड्राफ़्ट नीचे से हटाएँ। साइन किए गए संस्करण और रिकॉर्ड नहीं मिटते। यहाँ मिटाने से छपा हुआ काग़ज़ नहीं मिटता, उसे आप ख़ुद नष्ट करें।',
  };
  const ask: NoticeBlock = {
    titleEn: 'How to ask', titleHi: 'कैसे कहें',
    en: 'Use the buttons below, or tell your doctor or palliative care team. Ask them too if you want a copy on the Sahara server erased. During this pilot only made-up patients are used.',
    hi: 'नीचे के बटन इस्तेमाल करें, या अपने डॉक्टर या पैलिएटिव केयर टीम को बताएँ। Sahara सर्वर से कॉपी मिटवानी हो तो भी उन्हीं से कहें। इस पायलट में सिर्फ़ काल्पनिक मरीज़ों का डेटा है।',
  };
  const conversation: NoticeBlock = {
    titleEn: 'A recorded conversation', titleHi: 'रिकॉर्ड की गई बातचीत',
    en: 'If everyone agrees, the doctor can record the talk. The audio stays on this device and is deleted after review, unless the doctor keeps a copy. The written words and the note stay with the plan on this device. They are never put on the emergency card. They leave this device only if the doctor agrees to an AI reading, and the screen names the service first.',
    hi: 'अगर सभी राज़ी हों, तो डॉक्टर बातचीत रिकॉर्ड कर सकते हैं। ऑडियो इसी डिवाइस पर रहता है और समीक्षा के बाद मिट जाता है, जब तक डॉक्टर कॉपी न रखें। लिखे गए शब्द और नोट इसी डिवाइस पर योजना के साथ रहते हैं। वे इमरजेंसी कार्ड पर कभी नहीं जाते। वे इस डिवाइस से तभी बाहर जाते हैं जब डॉक्टर AI से पढ़वाने के लिए राज़ी हों, और स्क्रीन पहले सेवा का नाम बताती है।',
  };
  return scope === 'care-plan' ? [stored, why, where, conversation, rights, ask] : [stored, why, where, rights, ask];
}

export type DataExportInput = {
  patientId: string;
  ectprVersions: EctprRecord[]; distribution: EctprDistribution[]; audit: AuditEvent[];
  episodes: EpisodeLog[]; outcomes: OutcomeRecord[]; incidents: IncidentReport[];
  amdVersions: AmdVersion[];
  /** Scans of signed papers as data URLs, keyed by scan key. */
  scans: Record<string, { name: string; type: string; dataUrl: string }>;
  /** The card's secret manage key and token are left out on purpose. */
  cards: Array<{ issuedAt: string; version: number; server: string; revokedAt?: string }>;
};

export function buildDataExport(input: DataExportInput, now: Date = new Date()) {
  const { patientId, ...data } = input;
  return { format: 'Sahara personal data export', formatVersion: 1, exportedAt: now.toISOString(), patientId, storedOn: 'This device (browser storage)', ...data };
}

/** Scan keys held by this person's living will versions and signed plans, so the export can include the files. */
export function amdScanKeys(amdVersions: Array<Pick<AmdVersion, 'execution' | 'revocation'>>): string[] {
  return [...new Set(amdVersions.flatMap((v) => [v.execution?.scanKey, v.revocation?.scanKey]).filter((key): key is string => Boolean(key)))];
}

export function dataExportFileName(patientId: string, now: Date = new Date()): string {
  const safe = patientId.replace(/[^A-Za-z0-9_-]+/g, '-').replace(/^-+|-+$/g, '') || 'patient';
  return `sahara-my-data-${safe}-${now.toISOString().slice(0, 10)}.json`;
}
