import { ContinuityPrototype } from './continuity-prototype';
import { ContinuityProvider } from './continuity-context';

export default function Home() {
  return (
    <ContinuityProvider>
      <ContinuityPrototype />
    </ContinuityProvider>
  );
}
