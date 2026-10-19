import type { Metadata } from 'next';
import { ReviewedCentres } from './reviewed-centres';
export const metadata: Metadata = {title:'Reviewed hospital updates · Sahara',description:'Dated hospital information reviewed before publication.'};
export default function Page() { return <ReviewedCentres />; }
