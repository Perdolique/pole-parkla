import { defineCollection } from 'astro:content'
import { glob } from 'astro/loaders'
import { z } from 'astro/zod'

const privacy = defineCollection({
  loader: glob({
    pattern: '*.md',
    base: './src/data/privacy'
  }),

  schema: z.object({
    title: z.string().min(1),
    description: z.string().min(20),
    lastUpdated: z.date()
  })
})

const updates = defineCollection({
  loader: glob({
    pattern: '**/*.md',
    base: './src/data/updates'
  }),

  schema: z.object({
    version: z.string().regex(/^\d+\.\d+\.\d+(?:[-+][0-9A-Za-z.-]+)?$/),
    releasedAt: z.date(),
    title: z.string().min(1),
    highlights: z.array(z.string().min(1)).min(1),
    draft: z.boolean().default(false)
  })
})

export const collections = {
  privacy,
  updates
}
