import { ContinuityPrototype } from '../continuity-prototype';
import { ContinuityProvider } from '../continuity-context';

export default function CareLayout({ children }: { children: React.ReactNode }) {
  return <ContinuityProvider><ContinuityPrototype />{children}</ContinuityProvider>;
}
