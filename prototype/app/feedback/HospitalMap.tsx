'use client';

import { useEffect, useRef, useState } from 'react';
import type { Map, Marker } from 'maplibre-gl';
import 'maplibre-gl/dist/maplibre-gl.css';
import { searchHospitalLocations, supportedHospitalPin, type HospitalLocation } from './hospital-location';
import styles from './feedback.module.css';

export default function HospitalMap({latitude,longitude,locationLabel,onChange,onClear}:{latitude:string;longitude:string;locationLabel:string;onChange:(latitude:number,longitude:number,label:string)=>void;onClear:()=>void}) {
  const [query,setQuery]=useState(locationLabel);const [matches,setMatches]=useState<HospitalLocation[]>([]);const [busy,setBusy]=useState(false);const [message,setMessage]=useState('');
  const [mapOpen,setMapOpen]=useState(false);const [visible,setVisible]=useState(false);const [mapError,setMapError]=useState('');const [mapReady,setMapReady]=useState(false);const [streetLevel,setStreetLevel]=useState(false);
  const [candidate,setCandidate]=useState<{latitude:number;longitude:number}|null>(null);
  const container=useRef<HTMLDivElement>(null);const map=useRef<Map|null>(null);const marker=useRef<Marker|null>(null);const generation=useRef(0);const searchAbort=useRef<AbortController|null>(null);
  const placeMarker=useRef<((latitude:number,longitude:number)=>void)|null>(null);
  const callbacks=useRef({onChange,onClear});const current=useRef({latitude,longitude,candidate});
  useEffect(()=>{callbacks.current={onChange,onClear};},[onChange,onClear]);
  useEffect(()=>{current.current={latitude,longitude,candidate};},[latitude,longitude,candidate]);
  useEffect(()=>()=>{generation.current++;searchAbort.current?.abort();},[]);
  useEffect(()=>{if(!mapOpen||!container.current)return;const observer=new ResizeObserver(()=>{const shown=Boolean(container.current?.clientWidth&&container.current?.clientHeight);setVisible(shown);if(shown)map.current?.resize();else setMapReady(false);});observer.observe(container.current);return()=>observer.disconnect();},[mapOpen]);
  useEffect(()=>{
    if(!mapOpen||!visible||!container.current?.clientWidth)return;
    let disposed=false;let cleanup:(()=>void)|undefined;
    void import('maplibre-gl').then(L=>{
      if(disposed||!container.current?.clientWidth)return;
      let instance:Map;
      try { instance=new L.Map({container:container.current,style:{version:8,sources:{streets:{type:'raster',tiles:['https://tile.openstreetmap.org/{z}/{x}/{y}.png'],tileSize:256,attribution:'&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'}},layers:[{id:'streets',type:'raster',source:'streets'}]},center:[79,22],zoom:4,maxBounds:[[68,6],[98,38]],attributionControl:{compact:true},cooperativeGestures:true}); }
      catch {setMapError('The map could not start. Search and choose a place above instead.');return;}
      map.current=instance;
      let cleaned=false;
      cleanup=()=>{if(cleaned)return;cleaned=true;placeMarker.current=null;marker.current?.remove();marker.current=null;instance.remove();if(map.current===instance)map.current=null;};
      instance.addControl(new L.NavigationControl({showCompass:false}),'top-right');
      instance.getCanvas().setAttribute('aria-label','Hospital map. Arrow keys move the map; use the Use map center button to choose its center.');
      const place=(lat:number,lng:number)=>{
        if(!marker.current){marker.current=new L.Marker({color:'#B9472C',draggable:true}).setLngLat([lng,lat]).addTo(instance);marker.current.on('dragend',()=>{if(disposed)return;const position=marker.current?.getLngLat();generation.current++;searchAbort.current?.abort();setBusy(false);setMatches([]);callbacks.current.onClear();setCandidate(null);if(!position||!supportedHospitalPin(position.lat,position.lng)){marker.current?.remove();marker.current=null;setMessage('');setMapError('Choose a hospital within the supported map area.');return;}setMapError('');setCandidate({latitude:Number(position.lat.toFixed(6)),longitude:Number(position.lng.toFixed(6))});setMessage('Pin moved. Use this hospital pin to confirm.');});}
        else marker.current.setLngLat([lng,lat]);
      };
      placeMarker.current=place;
      const state=current.current;const lat=state.candidate?.latitude??(state.latitude?Number(state.latitude):NaN),lng=state.candidate?.longitude??(state.longitude?Number(state.longitude):NaN);
      if(supportedHospitalPin(lat,lng)){place(lat,lng);instance.jumpTo({center:[lng,lat],zoom:15});}
      instance.on('zoomend',()=>{if(!disposed)setStreetLevel(instance.getZoom()>=14);});
      instance.on('click',event=>{if(disposed)return;generation.current++;searchAbort.current?.abort();setBusy(false);setMatches([]);callbacks.current.onClear();setCandidate(null);if(instance.getZoom()<14){marker.current?.remove();marker.current=null;setMapError('Zoom in to the hospital’s street before placing its pin.');return;}if(!supportedHospitalPin(event.lngLat.lat,event.lngLat.lng)){marker.current?.remove();marker.current=null;setMapError('Choose a hospital within the supported map area.');return;}setMapError('');const next={latitude:Number(event.lngLat.lat.toFixed(6)),longitude:Number(event.lngLat.lng.toFixed(6))};place(next.latitude,next.longitude);setCandidate(next);setMessage('Pin placed. Use this hospital pin to confirm.');});
      instance.on('load',()=>{if(!disposed){setMapReady(true);setStreetLevel(instance.getZoom()>=14);setMapError('');}});
      instance.on('error',()=>{if(!disposed)setMapError('Some map tiles could not load. Search and choose a place above instead.');});
    }).catch(()=>{cleanup?.();if(!disposed)setMapError('The map could not load. Search and choose a place above instead.');});
    return()=>{disposed=true;cleanup?.();};
  },[mapOpen,visible]);
  useEffect(()=>{const lat=latitude?Number(latitude):NaN,lng=longitude?Number(longitude):NaN;if(supportedHospitalPin(lat,lng)&&map.current){placeMarker.current?.(lat,lng);map.current.jumpTo({center:[lng,lat],zoom:15});}else if(!candidate){marker.current?.remove();marker.current=null;}},[latitude,longitude,candidate]);
  async function search(){const token=++generation.current;searchAbort.current?.abort();const controller=new AbortController();searchAbort.current=controller;setBusy(true);setMessage('');setMatches([]);try{const found=await searchHospitalLocations(query,controller.signal);if(token!==generation.current||controller.signal.aborted)return;setMatches(found);setMessage(found.length?'Choose the hospital location below.':'No matching place found. Add the city to your search, or select a pin on the map.');}catch(e){if(token===generation.current&&!controller.signal.aborted)setMessage(e instanceof Error?e.message:'Search is unavailable. Try again or use the map.');}finally{if(token===generation.current)setBusy(false);}}
  function edit(value:string){generation.current++;searchAbort.current?.abort();setBusy(false);setQuery(value);setMatches([]);setMessage('');setCandidate(null);onClear();}
  function choose(place:HospitalLocation){generation.current++;searchAbort.current?.abort();setBusy(false);setCandidate(null);setQuery(place.label);setMatches([]);setMessage('Hospital location selected.');onChange(place.latitude,place.longitude,place.label);}
  function confirmPin(center=false){if(!map.current||map.current.getZoom()<14){setMapError('Zoom in to the hospital’s street before choosing its location.');return;}const point=center?map.current.getCenter():null;const lat=point?.lat??candidate?.latitude,lng=point?.lng??candidate?.longitude;if(!supportedHospitalPin(lat,lng)){setMapError('Move the map to the hospital, then choose its location.');return;}generation.current++;searchAbort.current?.abort();setBusy(false);setCandidate(null);setMatches([]);setQuery('Hospital pin selected');setMessage('Hospital location selected.');onChange(Number(lat!.toFixed(6)),Number(lng!.toFixed(6)),'Hospital pin selected');}
  const selected=Boolean(latitude&&longitude&&supportedHospitalPin(Number(latitude),Number(longitude)));
  return <div>
    <label htmlFor="hospital-location-search">Hospital name or address</label>
    <p className={styles.hint}>Search for the hospital, then choose a result or place its pin. Use a hospital, not a home address.</p>
    <p className={styles.hint}>Search sends the query and your IP address to Photon. Opening the map sends your IP address and viewed area to OpenStreetMap. No GPS permission is requested.</p>
    <div className={styles.locationSearch}><input id="hospital-location-search" type="search" value={query} maxLength={160} autoComplete="off" onChange={event=>edit(event.target.value)} onKeyDown={event=>{if(event.key==='Enter'){event.preventDefault();void search();}}} aria-describedby="hospital-location-status" /><button className={styles.secondary} type="button" onClick={()=>void search()} disabled={busy||query.trim().length<3}>{busy?'Searching…':'Search'}</button></div>
    <p id="hospital-location-status" role="status" className={styles.hint}>{message}</p>
    {matches.length?<ul className={styles.locationResults} aria-label="Hospital location results">{matches.map(match=><li key={match.id}><button type="button" className={styles.locationResult} onClick={()=>choose(match)}>{match.label}<span>Choose this location</span></button></li>)}</ul>:null}
    {matches.length>0||selected?<p className={styles.hint}>Place results: <a href="https://photon.komoot.io/" target="_blank" rel="noreferrer">Photon</a>, © <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noreferrer">OpenStreetMap contributors</a>.</p>:null}
    {selected?<p className={styles.locationSelected}><strong>Location selected</strong><br />{locationLabel||'Hospital pin selected'}<br /><small>The map pin is saved with your survey.</small></p>:null}
    <div className={styles.locationActions}><button type="button" className={styles.secondary} aria-expanded={mapOpen} onClick={()=>{setMapReady(false);setVisible(false);setMapError('');setMapOpen(value=>!value);}}>{mapOpen?'Close map':'Open map to place a pin'}</button></div>
    {!mapOpen?<p className={styles.hint}>You can choose a search result without opening the map.</p>:<><div className={styles.map} ref={container} role="region" aria-label="Hospital location map" /><p className={styles.hint}>Zoom in to the hospital’s street, click or drag a pin, then confirm it. Keyboard users can move the map with arrow keys and choose Use map center.</p><div className={styles.locationActions}><button type="button" className={styles.secondary} disabled={!candidate||!mapReady||!streetLevel} onClick={()=>confirmPin()}>Use this hospital pin</button><button type="button" className={styles.secondary} disabled={!mapReady||!streetLevel} onClick={()=>confirmPin(true)}>Use map center</button></div>{mapError?<p className={styles.error} role="status">{mapError}</p>:null}</>}
  </div>;
}
