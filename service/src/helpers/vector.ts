export function cosine(a: number[], b: number[]): number {
  let dot = 0
  let normA = 0
  let normB = 0
  for (let i = 0; i < a.length; i++) {
    const x = a[i]!
    const y = b[i] ?? 0
    dot += x * y
    normA += x * x
    normB += y * y
  }
  return normA === 0 || normB === 0 ? 0 : dot / Math.sqrt(normA * normB)
}

export function meanVector(vectors: number[][], weights: number[]): number[] | null {
  const totalWeight = weights.reduce((sum, w) => sum + w, 0)
  const first = vectors[0]
  if (!first || totalWeight === 0) return null
  const mean = new Array<number>(first.length).fill(0)
  vectors.forEach((vector, i) => {
    const share = weights[i]! / totalWeight
    vector.forEach((x, d) => {
      mean[d] = mean[d]! + x * share
    })
  })
  return mean
}
