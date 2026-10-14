/** Minimal browser pdfmake declarations for our PDF export. No extra @types package needed. */
declare module 'pdfmake/interfaces' {
  export type Content = Record<string, unknown>
  export interface TDocumentDefinitions {
    [key: string]: unknown
  }
}

declare module 'pdfmake/build/pdfmake' {
  import type { TDocumentDefinitions } from 'pdfmake/interfaces'

  const pdfMake: {
    addVirtualFileSystem(fonts: Record<string, string>): void
    createPdf(definition: TDocumentDefinitions): {
      download(fileName?: string): void
    }
  }
  export default pdfMake
}

declare module 'pdfmake/build/vfs_fonts' {
  const fonts: Record<string, string>
  export default fonts
}