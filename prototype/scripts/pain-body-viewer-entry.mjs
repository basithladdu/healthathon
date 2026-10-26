import * as THREE from 'three';
import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js';
import { OrbitControls } from 'three/addons/controls/OrbitControls.js';
import { classifyPainBodyPoint, quantizePainBodyPoint, validPainViewerState } from '../app/pain-body-map-state.ts';

const surface = document.getElementById('surface');
const status = document.getElementById('status');
let state = { selected: [], rating: null, hindi: false, points: [], readOnly: false };
let applyState = () => {};
let changeView = () => {};
let focusRegion = () => false;
let focusFull = () => {};
let currentView = 'front';
// Camera focus targets in normalized body coordinates (x: patient left +, y: feet 0 to head 1).
// span is the body-height fraction to frame; face is the side the camera looks from (null keeps the current side).
const FOCUS_REGIONS = {
  head: { x: 0, y: 0.94, span: 0.15, face: null },
  neck: { x: 0, y: 0.85, span: 0.1, face: null },
  chest: { x: 0, y: 0.73, span: 0.2, face: 'front' },
  abdomen: { x: 0, y: 0.54, span: 0.22, face: 'front' },
  'upper-back': { x: 0, y: 0.73, span: 0.2, face: 'back' },
  'lower-back': { x: 0, y: 0.54, span: 0.22, face: 'back' },
  'left-shoulder': { x: 0.62, y: 0.77, span: 0.16, face: null },
  'right-shoulder': { x: -0.62, y: 0.77, span: 0.16, face: null },
  'left-arm': { x: 0.72, y: 0.58, span: 0.4, face: null },
  'right-arm': { x: -0.72, y: 0.58, span: 0.4, face: null },
  'left-leg': { x: 0.3, y: 0.22, span: 0.46, face: null },
  'right-leg': { x: -0.3, y: 0.22, span: 0.46, face: null },
};

function emit(message) {
  if (window.SaharaPainNative?.postMessage) window.SaharaPainNative.postMessage(JSON.stringify(message));
  if (window.parent !== window) window.parent.postMessage(message, window.location.origin);
}

window.SaharaPainViewer = {
  setState(next) {
    if (!validPainViewerState(next)) return false;
    state = { selected: [...next.selected], rating: next.rating, hindi: next.hindi, points: (next.points ?? []).map(quantizePainBodyPoint), readOnly: next.readOnly === true };
    applyState();
    return true;
  },
  setView(view) { if (view === 'front' || view === 'back') changeView(view); },
  // Smoothly zoom to a body site id from PAIN_BODY_SITES; optional view picks front/back. Returns false for unknown ids.
  focusRegion(id, view) { return typeof id === 'string' && Object.prototype.hasOwnProperty.call(FOCUS_REGIONS, id) ? focusRegion(id, view === 'front' || view === 'back' ? view : null) : false; },
  focusFull() { focusFull(); },
};
window.addEventListener('message', (event) => {
  if (event.source !== window.parent || event.origin !== window.location.origin || !event.data || typeof event.data !== 'object') return;
  if (event.data.type === 'state') window.SaharaPainViewer.setState(event.data);
  if (event.data.type === 'view') window.SaharaPainViewer.setView(event.data.view);
  if (event.data.type === 'focus') window.SaharaPainViewer.focusRegion(event.data.region, event.data.view);
  if (event.data.type === 'focusFull') window.SaharaPainViewer.focusFull();
});

function fail() {
  status.textContent = state.hindi ? '3D नहीं खुला। नीचे जगह के बटन इस्तेमाल करें।' : '3D unavailable. Use the body-area buttons below.';
  emit({ type: 'error' });
}

try {
  const renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true, powerPreference: 'low-power' });
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 1.75));
  renderer.outputColorSpace = THREE.SRGBColorSpace;
  renderer.setClearColor(0xf7f9f4, 1);
  surface.appendChild(renderer.domElement);
  renderer.domElement.setAttribute('aria-label', 'Rotate the body with a drag; tap a body area to select it');
  const scene = new THREE.Scene();
  const camera = new THREE.PerspectiveCamera(30, 1, 0.01, 30);
  camera.position.set(0, 0.05, 3.95);
  scene.add(new THREE.HemisphereLight(0xffffff, 0x748777, 2.6));
  const light = new THREE.DirectionalLight(0xfff8f0, 2.9);
  light.position.set(-2, 3, 4); scene.add(light);
  const fill = new THREE.DirectionalLight(0xe6edf9, 1.4);
  fill.position.set(2, 0, -3); scene.add(fill);
  const controls = new OrbitControls(camera, renderer.domElement);
  controls.target.set(0, 0, 0); controls.enablePan = false; controls.enableDamping = false;
  controls.minDistance = 0.5; controls.maxDistance = 5.5;
  controls.minPolarAngle = Math.PI * 0.25; controls.maxPolarAngle = Math.PI * 0.75;
  let hasViewport = false;
  let usesPixelViewportHeight = false;
  const render = () => { if (hasViewport) renderer.render(scene, camera); };
  controls.addEventListener('change', render);
  let frame = null; // set once the model is loaded; world-space conversion of normalized coordinates
  let pendingFocus = null;
  let animation = 0;
  const stopAnimation = () => { if (animation) cancelAnimationFrame(animation); animation = 0; };
  const flyTo = (target, distance, view) => {
    stopAnimation();
    const fromTarget = controls.target.clone();
    const fromSpherical = new THREE.Spherical().setFromVector3(camera.position.clone().sub(fromTarget));
    const toSpherical = new THREE.Spherical(distance, Math.PI / 2 - 0.013, view === 'back' ? Math.PI : 0);
    let delta = toSpherical.theta - fromSpherical.theta;
    delta = Math.atan2(Math.sin(delta), Math.cos(delta));
    const started = performance.now(), duration = 450;
    const offset = new THREE.Vector3();
    const step = (now) => {
      const t = Math.min(1, (now - started) / duration), e = t * t * (3 - 2 * t);
      controls.target.lerpVectors(fromTarget, target, e);
      offset.setFromSpherical(new THREE.Spherical(
        fromSpherical.radius + (toSpherical.radius - fromSpherical.radius) * e,
        fromSpherical.phi + (toSpherical.phi - fromSpherical.phi) * e,
        fromSpherical.theta + delta * e));
      camera.position.copy(controls.target).add(offset);
      controls.update(); render();
      animation = t < 1 ? requestAnimationFrame(step) : 0;
    };
    animation = requestAnimationFrame(step);
  };
  changeView = (view) => { currentView = view; pendingFocus = null; stopAnimation(); controls.target.set(0, 0, 0); camera.position.set(0, 0.05, view === 'back' ? -3.95 : 3.95); controls.update(); render(); };
  const applyFocus = (id, view) => {
    const region = FOCUS_REGIONS[id];
    currentView = region.face ?? view ?? currentView;
    const target = new THREE.Vector3(region.x * frame.size.x / 2, frame.minY + region.y * frame.size.y, 0);
    const distance = THREE.MathUtils.clamp(region.span * frame.size.y * 1.25 / (2 * Math.tan(THREE.MathUtils.degToRad(camera.fov / 2))), 0.6, 5.5);
    flyTo(target, distance, currentView);
  };
  focusRegion = (id, view) => { if (!frame) { pendingFocus = { id, view }; return true; } applyFocus(id, view); return true; };
  focusFull = () => {
    pendingFocus = null;
    if (!frame) return;
    stopAnimation();
    flyTo(new THREE.Vector3(0, 0, 0), 3.95, currentView);
  };
  const resize = () => {
    // Some Android WebViews resolve viewport CSS units to zero even with a real innerHeight.
    // Use that measured viewport in pixels, and keep it in sync with actual viewport changes.
    if (usesPixelViewportHeight || surface.clientHeight <= 0) {
      const viewportHeight = window.visualViewport?.height || window.innerHeight;
      if (Number.isFinite(viewportHeight) && viewportHeight > 0) {
        usesPixelViewportHeight = true;
        const pixelHeight = `${Math.ceil(viewportHeight)}px`;
        for (const element of [document.documentElement, document.body, surface]) {
          if (element.style.height !== pixelHeight) element.style.height = pixelHeight;
        }
      }
    }
    const width = surface.clientWidth, height = surface.clientHeight;
    hasViewport = Number.isFinite(width) && Number.isFinite(height) && width > 1 && height > 1;
    if (!hasViewport) return false;
    renderer.setSize(width, height, false); camera.aspect = width / height; camera.updateProjectionMatrix(); render();
    return true;
  };
  new ResizeObserver(resize).observe(surface);
  window.addEventListener('resize', resize);
  window.visualViewport?.addEventListener('resize', resize);
  resize();
  renderer.domElement.addEventListener('webglcontextlost', (event) => { event.preventDefault(); fail(); });
  const raycaster = new THREE.Raycaster();
  const pointer = new THREE.Vector2();
  const point = new THREE.Vector3();
  const meshes = [];
  const neutral = new THREE.Color('#ced8ca');
  // Heat ramp, pain 0 (blue) to 10 (red) in steps of 2; keep in sync with PainBodyMapRules.heatStops in Kotlin.
  const HEAT = ['#2b59d9', '#16c1d9', '#3cc552', '#f2d531', '#f28a1f', '#d62a1f'].map((hex) => new THREE.Color(hex));
  const unratedGlow = new THREE.Color('#8d978c');
  const heatColor = (rating, target = new THREE.Color()) => {
    const x = Math.min(10, Math.max(0, rating)) / 2, i = Math.min(HEAT.length - 2, Math.floor(x));
    return target.copy(HEAT[i]).lerp(HEAT[i + 1], x - i);
  };
  // Heat-map glow: painted per fragment on the body material from world-space points (Gaussian falloff), so it stays smooth on a low-poly mesh.
  const MAX_GLOW = 12, GLOW_SIGMA = 0.045; // world units are about metres (body height 1.8)
  const glow = { count: { value: 0 }, pts: { value: Array.from({ length: MAX_GLOW }, () => new THREE.Vector3()) }, cols: { value: Array.from({ length: MAX_GLOW }, () => new THREE.Vector3()) }, strength: { value: 1 } };
  const makeBodyMaterial = () => {
    const material = new THREE.MeshStandardMaterial({ color: 0xffffff, vertexColors: true, roughness: 0.75, metalness: 0.02 });
    material.onBeforeCompile = (shader) => {
      shader.uniforms.uGlowCount = glow.count; shader.uniforms.uGlowPts = glow.pts; shader.uniforms.uGlowCols = glow.cols; shader.uniforms.uGlowStrength = glow.strength;
      shader.vertexShader = shader.vertexShader
        .replace('#include <common>', '#include <common>\nvarying vec3 vGlowPos;')
        .replace('#include <begin_vertex>', '#include <begin_vertex>\nvGlowPos =(modelMatrix * vec4(transformed, 1.0)).xyz;');
      shader.fragmentShader = shader.fragmentShader
        .replace('#include <common>', `#include <common>
varying vec3 vGlowPos;
uniform float uGlowCount;
uniform vec3 uGlowPts[${MAX_GLOW}];
uniform vec3 uGlowCols[${MAX_GLOW}];
uniform float uGlowStrength;`)
        .replace('#include <color_fragment>', `#include <color_fragment>
for (int i = 0; i < ${MAX_GLOW}; i++) {
  if (float(i) >= uGlowCount) break;
  vec3 gd = vGlowPos - uGlowPts[i];
  float g = exp(-dot(gd, gd) / (2.0 * ${(GLOW_SIGMA * GLOW_SIGMA).toFixed(6)})) * uGlowStrength;
  diffuseColor.rgb = mix(diffuseColor.rgb, uGlowCols[i], g);
}`);
    };
    material.customProgramCacheKey = () => 'sahara-pain-heat';
    return material;
  };

  new GLTFLoader().load('./body.glb', (gltf) => {
    const body = gltf.scene;
    const original = new THREE.Box3().setFromObject(body);
    const center = original.getCenter(new THREE.Vector3());
    const size = original.getSize(new THREE.Vector3());
    const scale = 1.8 / size.y;
    body.position.sub(center); body.scale.setScalar(scale);
    // The asset has an identity node transform and its source coordinates are centred.
    body.position.multiplyScalar(scale);
    scene.add(body); body.updateMatrixWorld(true);
    const bounds = new THREE.Box3().setFromObject(body);
    const worldSize = bounds.getSize(new THREE.Vector3());
    frame = { size: worldSize, minY: bounds.min.y };
    if (pendingFocus) { const request = pendingFocus; pendingFocus = null; applyFocus(request.id, request.view); }
    const normalize = (p) => ({ x: p.x / (worldSize.x / 2), y: (p.y - bounds.min.y) / worldSize.y, z: p.z / (worldSize.z / 2) });
    body.traverse((object) => {
      if (!object.isMesh) return;
      object.geometry = object.geometry.clone();
      const positions = object.geometry.getAttribute('position');
      const colors = new THREE.BufferAttribute(new Float32Array(positions.count * 3), 3);
      object.geometry.setAttribute('color', colors);
      object.material = makeBodyMaterial();
      const sites = [];
      for (let index = 0; index < positions.count; index++) {
        point.fromBufferAttribute(positions, index); object.localToWorld(point);
        sites.push(classifyPainBodyPoint(normalize(point)));
      }
      object.userData.painSites = sites; meshes.push(object);
    });
    applyState = () => {
      const selected = new Set(state.selected);
      const hasRating = state.rating !== null;
      const tint = hasRating ? heatColor(state.rating) : unratedGlow;
      const pointed = new Set(state.points.map((saved) => saved.site));
      // Parts marked without a tapped point keep a soft region tint; tapped points glow on the surface instead.
      const regionTint = neutral.clone().lerp(tint, 0.45);
      for (const mesh of meshes) {
        const colors = mesh.geometry.getAttribute('color');
        mesh.userData.painSites.forEach((site, index) => {
          const color = selected.has(site) && !pointed.has(site) ? regionTint : neutral;
          colors.setXYZ(index, color.r, color.g, color.b);
        });
        colors.needsUpdate = true;
      }
      const shown = state.points.slice(0, MAX_GLOW);
      shown.forEach((saved, index) => {
        glow.pts.value[index].set(saved.x * worldSize.x / 2, bounds.min.y + saved.y * worldSize.y, saved.z * worldSize.z / 2);
        glow.cols.value[index].set(tint.r, tint.g, tint.b);
      });
      glow.count.value = shown.length;
      glow.strength.value = hasRating ? 1 : 0.7;
      status.textContent = state.readOnly
        ? state.hindi ? 'सेव किया दर्द का मैप · घुमाने के लिए खींचें' : 'Saved pain map · drag to rotate'
        : state.hindi ? 'घुमाने के लिए खींचें · दर्द की जगह छुएँ' : 'Drag to rotate · tap where it hurts';
      renderer.domElement.setAttribute('aria-label', state.readOnly
        ? state.hindi ? 'सेव किया दर्द का मैप। घुमाने के लिए खींचें। जगह नहीं बदल सकती।' : 'Saved pain map. Drag to rotate. Locations cannot be changed.'
        : state.hindi ? 'शरीर घुमाने के लिए खींचें; दर्द की जगह छुएँ' : 'Rotate the body with a drag; tap a body area to select it');
      render();
    };
    let down = null;
    renderer.domElement.addEventListener('pointerdown', (event) => { if (event.isPrimary) stopAnimation(); if (event.isPrimary) down = { x: event.clientX, y: event.clientY }; });
    renderer.domElement.addEventListener('pointercancel', () => { down = null; });
    renderer.domElement.addEventListener('pointerup', (event) => {
      const start = down; down = null;
      if (state.readOnly || !start || !event.isPrimary || Math.hypot(event.clientX - start.x, event.clientY - start.y) > 7) return;
      const rect = renderer.domElement.getBoundingClientRect();
      pointer.set(((event.clientX - rect.left) / rect.width) * 2 - 1, -((event.clientY - rect.top) / rect.height) * 2 + 1);
      raycaster.setFromCamera(pointer, camera);
      const hit = raycaster.intersectObjects(meshes, false)[0];
      if (!hit) return;
      const normalized = normalize(hit.point);
      const site = classifyPainBodyPoint(normalized);
      if (site) { const selectedPoint = quantizePainBodyPoint({ site, ...normalized }); emit({ type: 'pick', site, point: { x: selectedPoint.x, y: selectedPoint.y, z: selectedPoint.z } }); }
    });
    applyState(); if (resize()) emit({ type: 'ready' }); else fail();
  }, undefined, fail);
} catch { fail(); }
