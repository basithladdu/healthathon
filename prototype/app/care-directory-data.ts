export type CareService = 'Clinic visits' | 'Inpatient care' | 'Home care';

export type CareCentre = {
  id: string;
  name: string;
  city: string;
  state: 'Karnataka' | 'Telangana' | 'Andhra Pradesh';
  address: string;
  phone: string;
  phoneNote?: string;
  services: CareService[];
  directoryUrl: string;
};

// Selected public listings transcribed from the linked Pallium India directories.
// This is a dated directory snapshot, not confirmation of service availability,
// RMI authorisation, medicine stock, a referral, or provider coordinates.
export const DIRECTORY_CHECKED_ON = '20 September 2026';
const karnataka = 'https://palliumindia.org/clinics/karnataka';
const telangana = 'https://palliumindia.org/clinics/telangana';
const andhraPradesh = 'https://palliumindia.org/clinics/andhrapradesh';

export const CARE_CENTRES: CareCentre[] = [
  {
    id: 'pallium-bengaluru', name: 'Pallium India OPD', city: 'Bengaluru', state: 'Karnataka',
    address: '4th B Block, 32nd E Cross Road, beside Rajiv Gandhi Health Institute, Tilak Nagar, Jayanagar, Bengaluru 560041',
    phone: '+917907301551', services: ['Clinic visits', 'Home care'], directoryUrl: karnataka,
  },
  {
    id: 'aster-cmi', name: 'Aster CMI Hospital', city: 'Bengaluru', state: 'Karnataka',
    address: '43/2, New Airport Road, NH-7, Outer Ring Road, Sahakar Nagar, Bengaluru 560092',
    phone: '+918043420100', services: ['Clinic visits', 'Inpatient care', 'Home care'], directoryUrl: karnataka,
  },
  {
    id: 'baptist-bengaluru', name: 'Bangalore Baptist Hospital — Palliative Care', city: 'Bengaluru', state: 'Karnataka',
    address: 'Bellary Road, Hebbal, Bengaluru 560024',
    phone: '+918022024395', services: ['Clinic visits', 'Inpatient care', 'Home care'], directoryUrl: karnataka,
  },
  {
    id: 'karunashraya', name: 'Karunashraya — Bangalore Hospice Trust', city: 'Bengaluru', state: 'Karnataka',
    address: 'Airport Varthur Main Road, Marathahalli, Kundalahalli Gate, Bengaluru 560037',
    phone: '+918042685666', services: ['Clinic visits', 'Inpatient care', 'Home care'], directoryUrl: karnataka,
  },
  {
    id: 'mnj-hyderabad', name: 'MNJ Institute of Oncology and Regional Cancer Centre', city: 'Hyderabad', state: 'Telangana',
    address: 'Red Hills, Lakdi Kapool, Hyderabad 500004',
    phone: '+914023397000', services: ['Clinic visits', 'Inpatient care', 'Home care'], directoryUrl: telangana,
  },
  {
    id: 'kumudini-devi', name: 'Kumudini Devi Palliative Care Centre', city: 'Hyderabad', state: 'Telangana',
    address: 'Pain Relief and Palliative Care Society, inside Ram Dev Rao Hospital, Kukatpally, Hyderabad',
    phone: '+919515336035', services: ['Inpatient care'], directoryUrl: telangana,
  },
  {
    id: 'sparsh-hospice', name: 'Sparsh Hospice', city: 'Hyderabad', state: 'Telangana',
    address: 'Sy No. 7/1/2, next to Oakridge International School, Khajaguda, Hyderabad 500008',
    phone: '+917995027879', services: ['Clinic visits', 'Inpatient care', 'Home care'], directoryUrl: telangana,
  },
  {
    id: 'basavatarakam', name: 'Basavatarakam Indo-American Cancer Hospital', city: 'Hyderabad', state: 'Telangana',
    address: 'Road No. 10, Banjara Hills, Hyderabad 500034',
    phone: '+914023551235', phoneNote: 'Ask for extension 2536',
    services: ['Clinic visits', 'Inpatient care', 'Home care'], directoryUrl: telangana,
  },
  {
    id: 'sviccar-tirupati', name: 'Sri Venkateswara Institute of Cancer Care and Advanced Research', city: 'Tirupati', state: 'Andhra Pradesh',
    address: 'Alipiri to Zoo Park Road, adjacent to 33 KV Substation, Tirupati',
    phone: '18001036123', services: [], directoryUrl: andhraPradesh,
  },
  {
    id: 'svims-tirupati', name: 'Sri Venkateshwara Institute of Medical Science', city: 'Tirupati', state: 'Andhra Pradesh',
    address: 'Alipiri Road, Sri Padmavati Mahila Visvavidyalayam, Tirupati 517507',
    phone: '+918772287777', services: ['Clinic visits', 'Inpatient care', 'Home care'], directoryUrl: andhraPradesh,
  },
  {
    id: 'aashraya-kovvur', name: 'Aashraya Hospice', city: 'Kovvur', state: 'Andhra Pradesh',
    address: '9.1.8/2, near Lakshmi Cafe Hotel, Kovvur 534350',
    phone: '+919866072574', services: ['Inpatient care'], directoryUrl: andhraPradesh,
  },
  {
    id: 'aiims-mangalagiri', name: 'AIIMS Mangalagiri', city: 'Mangalagiri', state: 'Andhra Pradesh',
    address: 'Mangalagiri, Guntur District, Andhra Pradesh 522503',
    phone: '+919493065718', services: ['Clinic visits', 'Inpatient care', 'Home care'], directoryUrl: andhraPradesh,
  },
];

export function filterCareCentres(query: string, state: string, service: string): CareCentre[] {
  const terms = query.trim().toLocaleLowerCase().replace(/bangalore/g, 'bengaluru').split(/\s+/).filter(Boolean);
  return CARE_CENTRES.filter((centre) => {
    const searchable = `${centre.name} ${centre.city} ${centre.state} ${centre.address}`.toLocaleLowerCase().replace(/bangalore/g, 'bengaluru');
    return (!state || centre.state === state)
      && (!service || centre.services.includes(service as CareService))
      && terms.every((term) => searchable.includes(term));
  });
}

export function centreDirectionsUrl(centre: CareCentre): string {
  return `https://www.google.com/maps/dir/?api=1&destination=${encodeURIComponent(`${centre.name}, ${centre.address}, India`)}`;
}
