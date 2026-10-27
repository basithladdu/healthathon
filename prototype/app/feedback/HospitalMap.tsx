'use client';

import { useEffect, useRef, useState } from 'react';
import type { Map, Marker } from 'maplibre-gl';
import 'maplibre-gl/dist/maplibre-gl.css';
import { searchHospitalLocations, supportedHospitalPin, type HospitalLocation } from './hospital-location';
import styles from './feedback.module.css';

type Pin={latitude:number;longitude:number;label:string};
type Props={latitude:string;longitude:string;locationLabel:string;confirmed:boolean;revision:number;hospitalName:string;hospitalAddress:string;state:string;city:string;onChange:(latitude:number,longitude:number,label:string)=>void;onAddress:(address:string)=>void;onClear:()=>void};

export default function HospitalMap({latitude,longitude,locationLabel,confirmed,revision,hospitalName,hospitalAddress,state,city,onChange,onAddress,onClear}:Props) {
  const [query,setQuery]=useState(hospitalName||hospitalAddress);const [matches,setMatches]=useState<HospitalLocation[]>([]);const [busy,setBusy]=useState(false);const [message,setMessage]=useState('');
  const [visible,setVisible]=useState(false);const [mapError,setMapError]=useState('');const [mapReady,setMapReady]=useState(false);const [streetLevel,setStreetLevel]=useState(false);const [mapAttempt,setMapAttempt]=useState(0);
  const [candidate,setCandidate]=useState<Pin|null>(()=>supportedHospitalPin(Number(latitude),Number(longitude))&&latitude&&longitude?{latitude:Number(latitude),longitude:Number(longitude),label:locationLabel}:null);
  const [previousRevision,setPreviousRevision]=useState(revision);
  const container=useRef<HTMLDivElement>(null);const map=useRef<Map|null>(null);const marker=useRef<Marker|null>(null);const generation=useRef(0);const searchAbort=useRef<AbortController|null>(null);
  const placeMarker=useRef<((latitude:number,longitude:number)=>void)|null>(null);
  const callbacks=useRef({onChange,onAddress,onClear});const current=useRef({candidate});
  useEffect(()=>{callbacks.current={onChange,onAddress,onClear};},[onChange,onAddress,onClear]);
  useEffect(()=>{current.current={candidate};},[candidate]);
  useEffect(()=>()=>{generation.current++;searchAbort.current?.abort();},[]);
  useEffect(()=>{generation.current++;searchAbort.current?.abort();},[revision]);
  useEffect(()=>{if(!container.current)return;const observer=new ResizeObserver(()=>{const shown=Boolean(container.current?.clientWidth&&container.current?.clientHeight);setVisible(shown);if(shown)map.current?.resize();else setMapReady(false);});observer.observe(container.current);return()=>observer.disconnect();},[]);
  useEffect(()=>{
    if(!visible||!container.current?.clientWidth)return;
    let disposed=false;let cleanup:(()=>void)|undefined;
    void import('maplibre-gl').then(L=>{
      if(disposed||!container.current?.clientWidth)return;
      let instance:Map;
      try {instance=new L.Map({container:container.current,style:{version:8,sources:{streets:{type:'raster',tiles:['https://tile.openstreetmap.org/{z}/{x}/{y}.png'],tileSize:256,attribution:'&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'}},layers:[{id:'streets',type:'raster',source:'streets'}]},center:[79,22],zoom:4,maxBounds:[[68,6],[98,38]],attributionControl:{compact:true},cooperativeGestures:true});}
      catch {setMapError('The map could not start. Retry the map below. Your answers are kept.');return;}
      map.current=instance;
      let cleaned=false;
      cleanup=()=>{if(cleaned)return;cleaned=true;placeMarker.current=null;marker.current?.remove();marker.current=null;instance.remove();if(map.current===instance)map.current=null;};
      instance.addControl(new L.NavigationControl({showCompass:false}),'top-right');
      instance.getCanvas().setAttribute('aria-label','Hospital map. Arrow keys move the map; use Place pin at map center to choose its center.');
      const preview=(lat:number,lng:number,label:string)=>{
        generation.current++;searchAbort.current?.abort();setBusy(false);setMatches([]);callbacks.current.onClear();
        if(!supportedHospitalPin(lat,lng)){setCandidate(null);setMapError('Choose a hospital within India.');return;}
        setCandidate({latitude:Number(lat.toFixed(6)),longitude:Number(lng.toFixed(6)),label});setMapError('');setMessage('Check the pin on the map, then confirm it.');
      };
      const place=(lat:number,lng:number)=>{
        if(!marker.current){marker.current=new L.Marker({color:'#B9472C',draggable:true}).setLngLat([lng,lat]).addTo(instance);marker.current.on('dragend',()=>{if(disposed)return;const position=marker.current?.getLngLat();if(position)preview(position.lat,position.lng,'Hospital pin adjusted on map');});}
        else marker.current.setLngLat([lng,lat]);
      };
      placeMarker.current=place;
      const pin=current.current.candidate;
      if(pin){place(pin.latitude,pin.longitude);instance.jumpTo({center:[pin.longitude,pin.latitude],zoom:16});}
      instance.on('zoomend',()=>{if(!disposed)setStreetLevel(instance.getZoom()>=14);});
      instance.on('click',event=>{if(disposed)return;if(instance.getZoom()<14){setMapError('Zoom in to the hospital’s street before placing its pin.');return;}place(event.lngLat.lat,event.lngLat.lng);preview(event.lngLat.lat,event.lngLat.lng,'Hospital pin placed on map');});
      let mapFailed=false;
      instance.on('load',()=>{if(!disposed&&!mapFailed){setMapReady(true);setStreetLevel(instance.getZoom()>=14);setMapError('');}});
      instance.on('idle',()=>{if(!disposed&&!mapFailed&&instance.areTilesLoaded()){setMapReady(true);setMapError('');}});
      instance.on('error',()=>{if(!disposed){mapFailed=true;setMapReady(false);setMapError('The map has not loaded fully. Retry the map before confirming the pin.');}});
    }).catch(()=>{cleanup?.();if(!disposed)setMapError('The map could not load. Retry the map below. Your answers are kept.');});
    return()=>{disposed=true;cleanup?.();};
  },[visible,mapAttempt]);
  useEffect(()=>{if(candidate&&map.current){placeMarker.current?.(candidate.latitude,candidate.longitude);map.current.jumpTo({center:[candidate.longitude,candidate.latitude],zoom:16});}else if(!candidate){marker.current?.remove();marker.current=null;}},[candidate]);
  async function search(){
    if(!state){setMessage('Choose the hospital’s state above before searching.');return;}
    const token=++generation.current;searchAbort.current?.abort();const controller=new AbortController();searchAbort.current=controller;setBusy(true);setMessage('');setMatches([]);
    try{const found=await searchHospitalLocations(query,controller.signal,{state,city});if(token!==generation.current||controller.signal.aborted)return;setMatches(found);setMessage(found.length?`Choose a result in ${state}, then check its pin below.`:`No matching place found in ${state}. Try a shorter hospital name with its area, or place the pin on the map.`);}
    catch(e){if(token===generation.current&&!controller.signal.aborted)setMessage(e instanceof Error?e.message:'Search is unavailable. Try again or use the map.');}
    finally{if(token===generation.current)setBusy(false);}
  }
  function edit(value:string){generation.current++;searchAbort.current?.abort();setBusy(false);setQuery(value);setMatches([]);setMessage('');setCandidate(null);onClear();}
  function enterManually(){generation.current++;searchAbort.current?.abort();setBusy(false);setMatches([]);setCandidate(null);onClear();setMessage('Enter whatever name and address you know above. You can continue without a map pin.');document.getElementById('hospital-hospitalAddress')?.focus();}
  function choose(place:HospitalLocation){generation.current++;searchAbort.current?.abort();setBusy(false);setCandidate({latitude:place.latitude,longitude:place.longitude,label:place.label});setQuery(place.label.slice(0,160));setMatches([]);setMessage('Address located. Check the hospital pin below, adjust it if needed, then confirm.');onClear();if(place.address)onAddress(place.address);}
  function placeAtCenter(){if(!map.current||map.current.getZoom()<14){setMapError('Zoom in to the hospital’s street before placing its pin.');return;}const point=map.current.getCenter();if(!supportedHospitalPin(point.lat,point.lng))return;onClear();setCandidate({latitude:Number(point.lat.toFixed(6)),longitude:Number(point.lng.toFixed(6)),label:'Hospital pin placed at map center'});setMessage('Check the pin on the map, then confirm it.');}
  function confirmPin(){if(!candidate||!map.current||!mapReady||map.current.getZoom()<14){setMapError('Load the map and zoom in to check the hospital pin before confirming.');return;}setMapError('');setMessage('Hospital pin confirmed.');onChange(candidate.latitude,candidate.longitude,candidate.label);}
  // Reset the draft selection when its hospital identity changes, without
  // destroying and downloading the MapLibre map on each typed character.
  if(previousRevision!==revision){setPreviousRevision(revision);setQuery(hospitalName||hospitalAddress);setCandidate(null);setMatches([]);setMessage('Hospital details changed. Search and check the pin again.');setBusy(false);}
  return <div>
    <label htmlFor="hospital-location-search">Hospital name and address</label>
    <p className={styles.hint}>Results are limited to {state||'the state you choose'}. Try part of the hospital name and its area; if you add a map pin, check it below.</p>
    <div className={styles.locationSearch}><input id="hospital-location-search" type="search" value={query} maxLength={160} autoComplete="off" onChange={event=>edit(event.target.value)} onKeyDown={event=>{if(event.key==='Enter'){event.preventDefault();void search();}}} aria-describedby="hospital-location-status" /><button className={styles.secondary} type="button" onClick={()=>void search()} disabled={busy||query.trim().length<3||!state}>{busy?'Searching…':'Search'}</button></div>
    <p id="hospital-location-status" role="status" className={styles.hint}>{message}</p>
    {matches.length?<ul className={styles.locationResults} aria-label="Hospital location results">{matches.map(match=><li key={match.id}><button type="button" className={styles.locationResult} onClick={()=>choose(match)}><strong>{match.name}</strong><span className={styles.locationAddress}>{match.address}</span><span>Show hospital pin</span></button></li>)}</ul>:null}
    <div className={styles.manualLocation}><p className={styles.hint}>Hospital not listed? Type what you know above. A map pin is optional.</p><button type="button" className={styles.secondary} onClick={enterManually}>Enter hospital manually</button></div>
    <p className={styles.hint}>Place search: <a href="https://photon.komoot.io/" target="_blank" rel="noreferrer">Photon</a>, © <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noreferrer">OpenStreetMap contributors</a>. Your query, selected city/state and IP address go to Photon; map tiles share your IP address and viewed area with OpenStreetMap. No GPS permission is requested.</p>
    <div className={styles.map} ref={container} role="region" aria-label="Hospital location map" />
    <p className={styles.hint}>Check that the pin is on the hospital. Drag it to correct the location, or zoom in and tap the map. Keyboard users can move with arrow keys and place the pin at the map center.</p>
    <div className={styles.locationActions}><button type="button" className={styles.secondary} disabled={!mapReady||!streetLevel} onClick={placeAtCenter}>Place pin at map center</button><button type="button" className={styles.secondary} disabled={!candidate||!mapReady||!streetLevel||confirmed} onClick={confirmPin}>{confirmed?'Hospital pin confirmed':'Confirm this hospital pin'}</button></div>
    {confirmed?<p className={styles.locationSelected}><strong>Hospital pin confirmed</strong><br />{locationLabel||'Hospital pin selected'}</p>:<p className={styles.hint}>You can continue without a map pin. If you add one, confirm it above.</p>}
    {mapError?<div><p className={styles.error} role="status">{mapError}</p><button type="button" className={styles.secondary} onClick={()=>{setMapError('');setMapReady(false);setMapAttempt(value=>value+1);}}>Retry map</button></div>:null}
  </div>;
}
