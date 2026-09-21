'use client';

import { CARE_CENTRES, centreDirectionsUrl, type CareCentre } from './care-directory-data';

export function CareDirectory({ centres = CARE_CENTRES, selectedId, locatingId, onSelect }: {
  centres?: CareCentre[];
  selectedId?: string | null;
  locatingId?: string | null;
  onSelect?: (centre: CareCentre) => void;
}): React.JSX.Element {
  return <div className="care-directory care-directory-map-list">
    {centres.map((centre) => <article key={centre.id} className={`care-centre${selectedId === centre.id ? ' is-selected' : ''}`}>
      <p className="care-centre-city">{centre.city} · {centre.state}</p>
      <h3>{onSelect ? <button type="button" className="care-centre-select" aria-pressed={selectedId === centre.id} onClick={() => onSelect(centre)}>{centre.name}<span aria-hidden="true">↗</span></button> : centre.name}</h3>
      <p className="care-centre-services">{centre.services.length ? centre.services.join(' · ') : 'Call for available services'}</p>
      <address>{centre.address}</address>
      {centre.phoneNote && <p className="care-directory-note">{centre.phoneNote}</p>}
      {locatingId === centre.id && <p className="care-directory-note" role="status">Finding this centre on the map…</p>}
      <div className="care-centre-actions">
        {onSelect && <button type="button" className="care-centre-route" aria-label={`Show route to ${centre.name}`} onClick={() => onSelect(centre)}>Show route</button>}
        <a href={`tel:${centre.phone}`} aria-label={`Call ${centre.name}: ${centre.phone}`}>Call {centre.phone}</a>
        <a href={centreDirectionsUrl(centre)} target="_blank" rel="noopener noreferrer" aria-label={`Directions to ${centre.name}`}>Directions ↗</a>
        <a href={centre.directoryUrl} target="_blank" rel="noopener noreferrer" aria-label={`Pallium India listing for ${centre.name}`}>Source ↗</a>
      </div>
    </article>)}
  </div>;
}

export function SupportResources(): React.JSX.Element {
  return <section className="care-support-resources" aria-label="Support resources">
    <h1>Family support</h1>
    <div className="care-resource-grid">
      <section>
        <h2>Family and peer support</h2>
        <a href="https://cansupport.org/" target="_blank" rel="noopener noreferrer">CanSupport ↗</a>
        <a href="https://www.cancersupportindia.org/directory?service_type=" target="_blank" rel="noopener noreferrer">Cancer Support India ↗</a>
        <a href="https://www.palliativecare.in/palliative-care-directory-of-india/" target="_blank" rel="noopener noreferrer">Indian Association of Palliative Care ↗</a>
      </section>
      <section>
        <h2>Prescribed medicines</h2>
        <p>Ask your care team or a listed centre where to fill your prescription. Medicine stock and morphine authorisation have not been checked here.</p>
      </section>
      <section>
        <h2>Living wills</h2>
        <a href="https://palliumindia.org/2024/06/living-will-and-attorney-authorisation" target="_blank" rel="noopener noreferrer">Pallium India and Vidhi resources ↗</a>
        <p>A care conversation note does not create an advance medical directive.</p>
      </section>
    </div>
  </section>;
}
