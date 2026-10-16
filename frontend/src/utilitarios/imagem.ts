/**
 * Reduz a foto no navegador antes de enviar: fotos de celular têm vários MB e o OCR
 * não precisa disso. Também evita estourar o limite de upload de quem hospeda o backend.
 */
export async function reduzirImagem(arquivo: File, ladoMaximo = 1600): Promise<Blob> {
  let bitmap: ImageBitmap
  try {
    bitmap = await createImageBitmap(arquivo)
  } catch {
    throw new Error('Não consegui abrir essa imagem. Use um arquivo JPG, PNG ou WebP.')
  }

  const escala = Math.min(1, ladoMaximo / Math.max(bitmap.width, bitmap.height))
  const canvas = document.createElement('canvas')
  canvas.width = Math.round(bitmap.width * escala)
  canvas.height = Math.round(bitmap.height * escala)
  canvas.getContext('2d')?.drawImage(bitmap, 0, 0, canvas.width, canvas.height)
  bitmap.close()

  return new Promise((resolver, rejeitar) => {
    canvas.toBlob(
      (blob) => (blob ? resolver(blob) : rejeitar(new Error('Não consegui preparar a imagem para envio.'))),
      'image/jpeg',
      0.85,
    )
  })
}
