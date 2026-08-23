import type { APIRoute } from 'astro'
import { createUpdatesFeed } from '../lib/rss'

export const GET: APIRoute = ({ site }) => createUpdatesFeed('et', site)
