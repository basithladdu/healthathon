import { notFound } from 'next/navigation';
import { careViewFromPath } from '../../care-routes';

export default async function CarePage({ params }: { params: Promise<{ screen: string[] }> }) {
  const { screen } = await params;
  const path = `/${screen.join('/')}`;
  if (path !== '/sign-in' && !careViewFromPath(path)) notFound();
  return null;
}
