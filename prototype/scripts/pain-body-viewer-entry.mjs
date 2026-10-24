import * as THREE from 'three';
import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js';
import { OrbitControls } from 'three/addons/controls/OrbitControls.js';
import { classifyPainBodyPoint, quantizePainBodyPoint, validPainViewerState } from '../app/pain-body-map-state.ts';

const surface = document.getElementById('surface');
const status = document.getElementById('status');
let state = { selected: [], rating: null, hindi: false, points: [] };
let applyState = () => {};
let changeView = () => {};

function emit(message) {
  if (window.SaharaPainNative?.postMessage) window.SaharaPainNative.postMessage(JSON.stringify(message));
  if (window.parent !== window) window.parent.postMessage(message, window.location.origin);
}

window.SaharaPainViewer = {
  setState(next) {
    if (!validPainViewerState(next)) return false;
    state = { selected: [...next.selected], rating: next.rating, hindi: next.hindi, points: (next.points ?? []).map(quantizePainBodyPoint) };
    applyState();
    return true;
  },
  setView(view) { if (view === 'front' || view === 'back') changeView(view); },
};
window.addEventListener('message', (event) => {
  if (event.source !== window.parent || event.origin !== window.location.origin || !event.data || typeof event.data !== 'object') return;
  if (event.data.type === 'state') window.SaharaPainViewer.setState(event.data);
  if (event.data.type === 'view') window.SaharaPainViewer.setView(event.data.view);
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
  controls.minDistance = 2.3; controls.maxDistance = 5.5;
  controls.minPolarAngle = Math.PI * 0.25; controls.maxPolarAngle = Math.PI * 0.75;
  const render = () => renderer.render(scene, camera);
  controls.addEventListener('change', render);
  changeView = (view) => { camera.position.set(0, 0.05, view === 'back' ? -3.95 : 3.95); controls.update(); render(); };
  const resize = () => {
    const width = Math.max(surface.clientWidth, 1), height = Math.max(surface.clientHeight, 1);
    renderer.setSize(width, height, false); camera.aspect = width / height; camera.updateProjectionMatrix(); render();
  };
  new ResizeObserver(resize).observe(surface); resize();
  renderer.domElement.addEventListener('webglcontextlost', (event) => { event.preventDefault(); fail(); });
  const raycaster = new THREE.Raycaster();
  const pointer = new THREE.Vector2();
  const point = new THREE.Vector3();
  const meshes = [];
  const markers = new THREE.Group(); scene.add(markers);
  const markerGeometry = new THREE.SphereGeometry(0.018, 12, 8);
  const neutral = new THREE.Color('#ced8ca');
  const low = new THREE.Color('#e4b3a5');
  const high = new THREE.Color('#ad302d');

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
    const normalize = (p) => ({ x: p.x / (worldSize.x / 2), y: (p.y - bounds.min.y) / worldSize.y, z: p.z / (worldSize.z / 2) });
    body.traverse((object) => {
      if (!object.isMesh) return;
      object.geometry = object.geometry.clone();
      const positions = object.geometry.getAttribute('position');
      const colors = new THREE.BufferAttribute(new Float32Array(positions.count * 3), 3);
      object.geometry.setAttribute('color', colors);
      object.material = new THREE.MeshStandardMaterial({ color: 0xffffff, vertexColors: true, roughness: 0.75, metalness: 0.02 });
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
      const tint = state.rating === 0 ? neutral : low.clone().lerp(high, (state.rating || 0) / 10);
      for (const mesh of meshes) {
        const colors = mesh.geometry.getAttribute('color');
        mesh.userData.painSites.forEach((site, index) => {
          const color = hasRating && selected.has(site) ? tint : neutral;
          colors.setXYZ(index, color.r, color.g, color.b);
        });
        colors.needsUpdate = true;
      }
      for (const marker of [...markers.children]) { markers.remove(marker); marker.material.dispose(); }
      for (const saved of state.points) {
        const marker = new THREE.Mesh(markerGeometry, new THREE.MeshStandardMaterial({ color: hasRating ? tint : 0x647865, roughness: 0.6 }));
        marker.position.set(saved.x * worldSize.x / 2, bounds.min.y + saved.y * worldSize.y, saved.z * worldSize.z / 2);
        markers.add(marker);
      }
      status.textContent = state.hindi ? 'घुमाने के लिए खींचें · दर्द की जगह छुएँ' : 'Drag to rotate · tap where it hurts';
      render();
    };
    let down = null;
    renderer.domElement.addEventListener('pointerdown', (event) => { if (event.isPrimary) down = { x: event.clientX, y: event.clientY }; });
    renderer.domElement.addEventListener('pointercancel', () => { down = null; });
    renderer.domElement.addEventListener('pointerup', (event) => {
      const start = down; down = null;
      if (!start || !event.isPrimary || Math.hypot(event.clientX - start.x, event.clientY - start.y) > 7) return;
      const rect = renderer.domElement.getBoundingClientRect();
      pointer.set(((event.clientX - rect.left) / rect.width) * 2 - 1, -((event.clientY - rect.top) / rect.height) * 2 + 1);
      raycaster.setFromCamera(pointer, camera);
      const hit = raycaster.intersectObjects(meshes, false)[0];
      if (!hit) return;
      const normalized = normalize(hit.point);
      const site = classifyPainBodyPoint(normalized);
      if (site) { const selectedPoint = quantizePainBodyPoint({ site, ...normalized }); emit({ type: 'pick', site, point: { x: selectedPoint.x, y: selectedPoint.y, z: selectedPoint.z } }); }
    });
    applyState(); resize(); emit({ type: 'ready' });
  }, undefined, fail);
} catch { fail(); }
