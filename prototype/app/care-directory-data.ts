export type CareService = 'Clinic visits' | 'Inpatient care' | 'Home care';

export type MorphineStatus = 'available' | 'unavailable' | 'unspecified';

export type CareCentre = {
  id: string;
  name: string;
  city: string;
  district?: string;
  state: 'Karnataka' | 'Telangana' | 'Andhra Pradesh';
  address: string;
  /** The source's first/primary phone string, kept in its published format. */
  phone: string;
  /** Additional phone strings when the source lists more than one number. */
  phoneNumbers?: string[];
  phoneNote?: string;
  services: CareService[];
  /** The source's service wording, retained alongside the normalized filter values. */
  listedServices?: string;
  morphine?: MorphineStatus;
  morphineNote?: string;
  directoryUrl: string;
  sourceURL?: string;
  sourceAsOf?: string;
  coordinates?: [number, number];
};

// This is a dated directory snapshot, not confirmation of service availability,
// RMI authorisation, medicine stock, a referral, or provider coordinates.
export const DIRECTORY_CHECKED_ON = '2 October 2026';
const KARNATAKA_SOURCE_AS_OF = '2026-10-02';
const karnataka = 'https://palliumindia.org/clinics/karnataka';
const telangana = 'https://palliumindia.org/clinics/telangana';
const andhraPradesh = 'https://palliumindia.org/clinics/andhrapradesh';

type KarnatakaCentreInput = Omit<CareCentre, 'state' | 'directoryUrl' | 'sourceURL' | 'sourceAsOf'>;

function karnatakaCentre(input: KarnatakaCentreInput): CareCentre {
  return {
    ...input,
    state: 'Karnataka',
    directoryUrl: karnataka,
    sourceURL: karnataka,
    sourceAsOf: KARNATAKA_SOURCE_AS_OF,
  };
}

// Complete public Karnataka table transcribed from the Pallium India directory.
// services are normalized for the existing filters; listedServices preserves the
// source wording where it contains more than OP/IP/Home Care.
const KARNATAKA_DIRECTORY_CENTRES: CareCentre[] = [
  karnatakaCentre({
    id: 'pallium-bengaluru', name: 'Pallium India OPD', city: 'Bengaluru', district: 'Bengaluru Urban',
    address: '4th B Block, 32nd E Cross Rd, beside Rajiv Gandhi Health Institute, Tilak Nagar, Jayanagar, Bengaluru, Karnataka 560041',
    phone: '79073 01551', phoneNumbers: ['79073 01551'], services: ['Clinic visits', 'Home care'],
    listedServices: 'OP /Home Care', morphine: 'unavailable',
  }),
  karnatakaCentre({
    id: 'aster-cmi', name: 'Aster CMI Hospital', city: 'Bengaluru', district: 'Bengaluru Urban',
    address: '43/2, New Airport Road, NH-7, Outer Ring Rd, Sahakar Nagar, Bengaluru, Karnataka 560092',
    phone: '080-4342 0100, 9513243552, 9886179793', phoneNumbers: ['080-4342 0100', '9513243552', '9886179793'],
    services: ['Clinic visits', 'Inpatient care', 'Home care'], listedServices: 'OP / IP / Home Care', morphine: 'available',
  }),
  karnatakaCentre({
    id: 'baptist-bengaluru', name: 'Bangalore Baptist Hospital', city: 'Bengaluru', district: 'Bengaluru Urban',
    address: 'Palliative Care Program, Bellary Road, Hebbal, Bangalore Karnataka 560024',
    phone: '08022024395', phoneNumbers: ['08022024395'], services: ['Clinic visits', 'Inpatient care', 'Home care'],
    listedServices: 'OP / IP / Home care', morphine: 'available',
  }),
  karnatakaCentre({
    id: 'karunashraya', name: 'Karunashraya – Bangalore Hospice Trust', city: 'Bengaluru', district: 'Bengaluru Urban',
    address: 'Airport Varthur Main Road, Marathahalli, Kundalahalli Gate, Bangalore 560037',
    phone: '80 42685666', phoneNumbers: ['80 42685666'], phoneNote: 'The source also lists fax 80 28476201.',
    services: ['Clinic visits', 'Inpatient care', 'Home care'], listedServices: 'OP / IP / Home care', morphine: 'available',
  }),
  karnatakaCentre({
    id: 'kidwai-memorial', name: 'Kidwai Memorial Institute of Oncology', city: 'Bengaluru', district: 'Bengaluru Urban',
    address: 'Department of Palliative Care, Dr M.H. Marigowda Road, Bangalore Karnataka 560 029',
    phone: '080-66697999', phoneNumbers: ['080-66697999'], phoneNote: 'The source lists palliative care HOD extension 3101.',
    services: ['Clinic visits', 'Inpatient care'], listedServices: 'OP / IP', morphine: 'available',
  }),
  karnatakaCentre({
    id: 'samraksha', name: 'Samraksha', city: 'Bengaluru', district: 'Bengaluru Urban',
    address: '6th Main, A Cross, Bull Temple Road, Chamrajpet, Bangalore',
    phone: '90330 00230', phoneNumbers: ['90330 00230'], services: ['Clinic visits', 'Home care'],
    listedServices: 'OP / Home care', morphine: 'unavailable',
  }),
  karnatakaCentre({
    id: 'st-johns-bengaluru', name: 'St John’s Medical College', city: 'Bengaluru', district: 'Bengaluru Urban',
    address: 'Department of Palliative Care, Sarjapur Road, Bangalore Karnataka 560 034',
    phone: '080-2206 5913, 74119 16820', phoneNumbers: ['080-2206 5913', '74119 16820'],
    services: ['Clinic visits', 'Inpatient care', 'Home care'], listedServices: 'OP / IP / Home care', morphine: 'available',
  }),
  karnatakaCentre({
    id: 'mazumdar-shaw', name: 'Mazumdar Shaw Medical Center', city: 'Bengaluru', district: 'Bengaluru Urban',
    address: '258/A, Bommasandra Industrial Area, Ankeal Taluk, Bangalore-560099, Karnataka',
    phone: '+91 99709 75241', phoneNumbers: ['+91 99709 75241'], services: ['Clinic visits', 'Inpatient care', 'Home care'],
    listedServices: 'OP / IP / Home care', morphine: 'available',
  }),
  karnatakaCentre({
    id: 'athulya-assisted-living', name: 'Athulya Assisted Living', city: 'Bengaluru', district: 'Bengaluru Urban',
    address: 'S.V. Towers, #99/1, Old No. 45, Parappana Agrahara Kasavanahalli, Hosa Rd, Bengaluru, Karnataka 560100',
    phone: '98849 45900 / 98846 39400', phoneNumbers: ['98849 45900', '98846 39400'],
    services: ['Inpatient care', 'Home care'], listedServices: 'IP / Home care', morphine: 'available',
  }),
  karnatakaCentre({
    id: 'hcg-bangalore-oncology', name: 'HCG Bangalore institute of Oncology', city: 'Bengaluru', district: 'Bengaluru Urban',
    address: '8, P. Kalinga Road, Sampangi Ram Nagar, Bangalore',
    phone: '91 9886770724', phoneNumbers: ['91 9886770724'], services: ['Inpatient care'],
    listedServices: 'IP.', morphine: 'available',
  }),
  karnatakaCentre({
    id: 'svym-bengaluru', name: 'SVYM (Palliative care program), Bengaluru', city: 'Bengaluru', district: 'Bengaluru Urban',
    address: 'DVG Smaraka Bhavana, Gokhale Institute of Public Affairs, No.2/86/1- A, 5th Main, Bull Temple Road, NR Colony, Bengaluru.',
    phone: '96060 82501', phoneNumbers: ['96060 82501'], services: ['Home care'],
    listedServices: 'Home care.', morphine: 'available',
  }),
  karnatakaCentre({
    id: 'sarv-aveksha', name: 'Sarv Aveksha', city: 'Bengaluru', district: 'Bengaluru Urban',
    address: 'A-109, East wing, Central Dew Apartment, Hennur Bande, Bangalore-560042',
    phone: '74117 65252', phoneNumbers: ['74117 65252'], services: ['Home care'], listedServices: 'Home care.',
    morphine: 'unavailable',
  }),
  karnatakaCentre({
    id: 'hcb-bengaluru', name: 'HCB Bengaluru Oncology Centre', city: 'Bengaluru', district: 'Bengaluru Urban',
    address: 'No. 44 – 45/2 2nd Cross, Double Rd, off Lalbagh Road, Raja Ram Mohanroy Extension, Shanti Nagar, Bengaluru, Karnataka 560027',
    phone: '063588 88802', phoneNumbers: ['063588 88802'], phoneNote: 'The source publishes this number as 063588 88802.',
    services: ['Clinic visits', 'Inpatient care'], listedServices: 'OP / IP.', morphine: 'available',
  }),
  karnatakaCentre({
    id: 'oblf-geriatric-palliative', name: 'OBLF Geriatric and Palliative Care Center', city: 'Bengaluru', district: 'Bengaluru Urban',
    address: 'No. 17, Sai’s Anandam Layout, Nisarga Sarovara Apartment Road, Surya City Phase 1, Chandapura, Bengaluru – 560099.',
    phone: '96069 68964', phoneNumbers: ['96069 68964'], services: ['Clinic visits'], listedServices: 'OP.',
    morphine: 'unavailable',
  }),
  karnatakaCentre({
    id: 'portea-health-care', name: 'Portea Health Care', city: 'Bengaluru', district: 'Bengaluru Urban',
    address: 'Portea Medical, 69/B, 1st Stage, 1st Cross Road, Domlur, Bengaluru, Karnataka 560071',
    phone: '1800 121 2323', phoneNumbers: ['1800 121 2323'], services: ['Inpatient care', 'Home care'],
    listedServices: 'IP / Home care.', morphine: 'unavailable',
  }),
  karnatakaCentre({
    id: 'seraphean', name: 'Seraphean Palliative Care Services (A unit of Seraphean Health Private Limited)', city: 'Bengaluru', district: 'Bengaluru Urban',
    address: '14/1 “Shri Matha”, 3rd Cross, 60 feet road, 2nd Block, RMV 2nd Stage, Bangalore 560094',
    phone: '+91-98809112486 / +91-9980744775', phoneNumbers: ['+91-98809112486', '+91-9980744775'],
    listedServices: 'Onco-palliative care; Elder care; End of life care; PC for chronic diseases; Nursing services for patient and care giver support',
    services: [], morphine: 'available',
  }),
  karnatakaCentre({
    id: 'ramaiah-memorial', name: 'Ramaiah Memorial Hospital', city: 'Bengaluru', district: 'Bengaluru Urban',
    address: 'New BEL Road, M S Ramaiah Nagar, MSRIT PO, Bengalaru, Karnataka-560054',
    phone: '9810785246', phoneNumbers: ['9810785246'], services: ['Clinic visits', 'Inpatient care'],
    listedServices: 'Integrated Oncology OP / IP', morphine: 'available',
  }),
  karnatakaCentre({
    id: 'manipal-yelahanka', name: 'Manipal Hospital Yelahanka', city: 'Bengaluru', district: 'Bengaluru Urban',
    address: 'No 23/3, next to brigade honda showroom, venkatala village, yelahanka hobli, Bangalore North, Karnataka- 560064',
    phone: '9035846032', phoneNumbers: ['9035846032'], services: ['Clinic visits', 'Inpatient care'],
    listedServices: 'OP / IP', morphine: 'available',
  }),
  karnatakaCentre({
    id: 'pushpa-hospital-chikmagaluru', name: 'Pushpa Hospital', city: 'Chikmaglur', district: 'Chikkamagaluru',
    address: 'Narshimaraipura, Chikmaglur',
    phone: '91 9449651252', phoneNumbers: ['91 9449651252'], services: ['Clinic visits', 'Inpatient care'],
    listedServices: 'OP / IP', morphine: 'unavailable',
  }),
  karnatakaCentre({
    id: 'ave-maria-mangalore', name: 'Ave Maria Centre for Palliative care', city: 'Mangalore', district: 'Dakshina Kannada',
    address: 'Mangalanagara Kudupu, Vamanjoor Mangalore',
    phone: '(91) 70226 20186', phoneNumbers: ['(91) 70226 20186'], services: ['Home care'],
    listedServices: 'Home care', morphine: 'available',
  }),
  karnatakaCentre({
    id: 'kasturba-mangalore', name: 'Kasturba Medical College', city: 'Mangalore', district: 'Dakshina Kannada',
    address: 'PO Box 53, Light House Hill Road, Mangalore, 575001',
    phone: '9448189480', phoneNumbers: ['9448189480'], services: ['Clinic visits', 'Inpatient care'],
    listedServices: 'OP / IP', morphine: 'available',
  }),
  karnatakaCentre({
    id: 'svym-dharwad', name: 'SVYM (Palliative care program), Dharwad', city: 'Dharwad', district: 'Dharwad',
    address: 'First Floor, Academic Block, Dharwad Institute of Mental Health and Neurosciences (DIMHANS), Near suburban police station, Belagavi Road, Dharwad 580008',
    phone: '96064 92095', phoneNumbers: ['96064 92095'], services: ['Home care'], listedServices: 'Home care.',
    morphine: 'available',
  }),
  karnatakaCentre({
    id: 'hubbali-hospice', name: 'Hubbali Hospice Centre', city: 'Hubbali', district: 'Dharwad',
    address: 'Gamanagatti Khb Layout, Hubbali, Dharwad – 580009',
    phone: '79471 09679', phoneNumbers: ['79471 09679'], services: ['Inpatient care'],
    listedServices: 'IP.', morphine: 'available',
  }),
  karnatakaCentre({
    id: 'svym-hassan', name: 'SVYM (Palliative care program), Hassan', city: 'Hassan', district: 'Hassan',
    address: 'Room number 16C, Teaching Hospital, Hassan Institute of Medical Sciences (HIMS), Hassan.',
    phone: '99000 20487', phoneNumbers: ['99000 20487'], services: ['Home care'], listedServices: 'Home care.',
    morphine: 'available',
  }),
  karnatakaCentre({
    id: 'mysore-palliative-care', name: 'Mysore Palliative Care Center', city: 'Mysuru', district: 'Mysuru',
    address: 'B. N. Street, Akki chowka, Near K. R. Hospital',
    phone: '0821-2974133, 9686666155', phoneNumbers: ['0821-2974133', '9686666155'],
    services: ['Inpatient care', 'Home care'], listedServices: 'IP / Home care', morphine: 'available',
  }),
  karnatakaCentre({
    id: 'svym-mysuru', name: 'Swamy Vivekananda Youth Movement Palliative care centre', city: 'Mysuru', district: 'Mysuru',
    address: 'PKTB and CD Hospital Campus, KRS Road, Mysuru',
    phone: '96866 66155', phoneNumbers: ['96866 66155'], services: ['Clinic visits', 'Inpatient care', 'Home care'],
    listedServices: 'OP / IP / Home care', morphine: 'available',
  }),
  karnatakaCentre({
    id: 'svym-saragur', name: 'SVYM (Palliative care program), Saragur', city: 'Saragur', district: 'Mysuru',
    address: 'Vivekananda Memorial Hospital, Hanchipura Road, Sargur taluk, Mysuru dist., Karnataka 571121',
    phone: '90665 68500', phoneNumbers: ['90665 68500'], services: ['Clinic visits', 'Inpatient care', 'Home care'],
    listedServices: 'OP / IP / Home care.', morphine: 'available',
  }),
  karnatakaCentre({
    id: 'sharanya-hospice', name: 'Sharanya Hospice', city: 'Shimoga', district: 'Shivamogga',
    address: 'Service project, Opp Modern Talkies, Garden Area, B H Road, Shimoga',
    phone: '08182 223366', phoneNumbers: ['08182 223366'], services: ['Clinic visits', 'Inpatient care'],
    listedServices: 'OP / IP', morphine: 'unavailable',
  }),
  karnatakaCentre({
    id: 'navajeevan-shivamogga', name: 'Navajeeevan Holistic And Palliative Care Centre', city: 'Shivamogga', district: 'Shivamogga',
    address: 'Malnad Social Service Society, St. Joseph Chruch Compound, Sagar Road, Shivamogga,Dist: Shimoga',
    phone: '7022604714', phoneNumbers: ['7022604714'], services: ['Clinic visits', 'Inpatient care', 'Home care'],
    listedServices: 'OP / IP/ HOMECARE', morphine: 'unavailable',
  }),
  karnatakaCentre({
    id: 'kasturba-manipal', name: 'Kasturba Hospital', city: 'Manipal', district: 'Udupi',
    address: 'Madhav nagar, Manipal, Karnataka',
    phone: '82025 71201 /0820 2571201', phoneNumbers: ['82025 71201', '0820 2571201'],
    services: ['Clinic visits', 'Inpatient care', 'Home care'], listedServices: 'OP / IP / Home Care.', morphine: 'available',
  }),
  karnatakaCentre({
    id: 'vanaprastha-kolar', name: 'Vanaprastha Home, Palliative care centre', city: 'Kolar', district: 'Kolar',
    address: 'Keseregere, Masthi PO, Malur Tk, KOLAR, KARNATAKA 563139',
    phone: '8884636709', phoneNumbers: ['8884636709'], services: ['Clinic visits', 'Inpatient care', 'Home care'],
    listedServices: 'OP / IP / Home Care.', morphine: 'unavailable',
  }),
  karnatakaCentre({
    id: 'aadhya-spine-pain', name: 'Aadhya Spine & Pain Clinic', city: 'Chikodi', district: 'Belagavi (Belgaum)',
    address: 'Priyanka Children’s Hospital, Jayanagar, Chikodi- 591201',
    phone: '8310053818/ 9664348138', phoneNumbers: ['8310053818', '9664348138'], services: ['Clinic visits', 'Inpatient care'],
    listedServices: 'OP / IP', morphine: 'unavailable',
  }),
  karnatakaCentre({
    id: 'niram-tumkur', name: 'Niram Palliative Care Centre', city: 'Tumkur', district: 'Tumkur',
    address: 'Rural India Mission Hospital Campus, Sira Road, Tumkur, Karnataka – 572106',
    phone: '+91 8113827540 / 8050931144', phoneNumbers: ['+91 8113827540', '8050931144'],
    services: ['Clinic visits', 'Inpatient care', 'Home care'],
    listedServices: 'OP / IP / Homecare / Counselling', morphine: 'available',
    morphineNote: 'Pallium specifies: under a registered doctor’s licence.',
  }),
];

export const CARE_CENTRES: CareCentre[] = [
  ...KARNATAKA_DIRECTORY_CENTRES,
  {
    id: 'mnj-hyderabad', name: 'MNJ Institute of Oncology and Regional Cancer Centre', city: 'Hyderabad', state: 'Telangana',
    address: 'Red Hills, Lakdi Kapool, Hyderabad 500004',
    phone: '+914023397000', services: ['Clinic visits', 'Inpatient care', 'Home care'], directoryUrl: telangana, sourceURL: telangana,
  },
  {
    id: 'kumudini-devi', name: 'Kumudini Devi Palliative Care Centre', city: 'Hyderabad', state: 'Telangana',
    address: 'Pain Relief and Palliative Care Society, inside Ram Dev Rao Hospital, Kukatpally, Hyderabad',
    phone: '+919515336035', services: ['Inpatient care'], directoryUrl: telangana, sourceURL: telangana,
  },
  {
    id: 'sparsh-hospice', name: 'Sparsh Hospice', city: 'Hyderabad', state: 'Telangana',
    address: 'Sy No. 7/1/2, next to Oakridge International School, Khajaguda, Hyderabad 500008',
    phone: '+917995027879', services: ['Clinic visits', 'Inpatient care', 'Home care'], directoryUrl: telangana, sourceURL: telangana,
  },
  {
    id: 'basavatarakam', name: 'Basavatarakam Indo-American Cancer Hospital', city: 'Hyderabad', state: 'Telangana',
    address: 'Road No. 10, Banjara Hills, Hyderabad 500034',
    phone: '+914023551235', phoneNote: 'Ask for extension 2536',
    services: ['Clinic visits', 'Inpatient care', 'Home care'], directoryUrl: telangana, sourceURL: telangana,
  },
  {
    id: 'sviccar-tirupati', name: 'Sri Venkateswara Institute of Cancer Care and Advanced Research', city: 'Tirupati', state: 'Andhra Pradesh',
    address: 'Alipiri to Zoo Park Road, adjacent to 33 KV Substation, Tirupati',
    phone: '18001036123', services: [], directoryUrl: andhraPradesh, sourceURL: andhraPradesh,
  },
  {
    id: 'svims-tirupati', name: 'Sri Venkateshwara Institute of Medical Science', city: 'Tirupati', state: 'Andhra Pradesh',
    address: 'Alipiri Road, Sri Padmavati Mahila Visvavidyalayam, Tirupati 517507',
    phone: '+918772287777', services: ['Clinic visits', 'Inpatient care', 'Home care'], directoryUrl: andhraPradesh, sourceURL: andhraPradesh,
  },
  {
    id: 'aashraya-kovvur', name: 'Aashraya Hospice', city: 'Kovvur', state: 'Andhra Pradesh',
    // Contact details updated from the centre's own website on 21 September 2026.
    address: 'Doctors Cooperative Hospital, Kovvuru–Chagallu Road, Kovvur, Andhra Pradesh',
    phone: '+919000544574', services: ['Inpatient care'], directoryUrl: 'https://aashrayafoundation.ngo/', sourceURL: 'https://aashrayafoundation.ngo/',
  },
  {
    id: 'aiims-mangalagiri', name: 'AIIMS Mangalagiri', city: 'Mangalagiri', state: 'Andhra Pradesh',
    address: 'Mangalagiri, Guntur District, Andhra Pradesh 522503',
    phone: '+919493065718', services: ['Clinic visits', 'Inpatient care', 'Home care'], directoryUrl: andhraPradesh, sourceURL: andhraPradesh,
  },
];

export const KARNATAKA_CENTRES = CARE_CENTRES.filter((centre) => centre.state === 'Karnataka');

/** Return the state-restricted directory used by the Karnataka-facing surface. */
export function defaultCareCentreFilter(centres: CareCentre[] = CARE_CENTRES): CareCentre[] {
  return centres.filter((centre) => centre.state === 'Karnataka');
}

export function filterKarnatakaCareCentres(query = '', service = '', morphine?: string): CareCentre[] {
  return filterCareCentres(query, 'Karnataka', service, morphine);
}

function normalizeMorphineStatus(value: string): MorphineStatus | '' {
  const normalized = value.trim().toLocaleLowerCase().replace(/[_\s]+/g, '-');
  if (!normalized) return '';
  if (normalized === 'notavailable' || normalized === 'not-available' || normalized === 'unavailable') return 'unavailable';
  if (normalized === 'available') return 'available';
  if (normalized === 'unspecified' || normalized === 'unknown') return 'unspecified';
  return normalized as MorphineStatus;
}

export function filterCareCentres(query: string, state: string, service: string, morphine?: string): CareCentre[] {
  const terms = query.trim().toLocaleLowerCase().replace(/bangalore/g, 'bengaluru').split(/\s+/).filter(Boolean);
  const morphineFilter = morphine ? normalizeMorphineStatus(morphine) : '';
  return CARE_CENTRES.filter((centre) => {
    const searchable = `${centre.name} ${centre.city} ${centre.district ?? ''} ${centre.state} ${centre.address}`
      .toLocaleLowerCase().replace(/bangalore/g, 'bengaluru');
    const centreMorphine = centre.morphine ?? 'unspecified';
    return (!state || centre.state === state)
      && (!service || centre.services.includes(service as CareService))
      && (!morphineFilter || centreMorphine === morphineFilter)
      && terms.every((term) => searchable.includes(term));
  });
}

export function centreDirectionsUrl(centre: CareCentre): string {
  const destination = centre.coordinates ? `${centre.coordinates[1]},${centre.coordinates[0]}` : `${centre.name}, ${centre.address}, India`;
  return `https://www.google.com/maps/dir/?api=1&destination=${encodeURIComponent(destination)}`;
}
