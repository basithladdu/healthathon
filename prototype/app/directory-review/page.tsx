import type { Metadata } from 'next';
import { DirectoryReview } from './directory-review';
export const metadata:Metadata={title:'Hospital review queue · Sahara',robots:{index:false,follow:false},referrer:'no-referrer'};
export default function Page(){return <DirectoryReview/>;}
