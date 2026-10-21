import { Pipe, PipeTransform, inject } from '@angular/core';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import DOMPurify from 'dompurify';
import { marked } from 'marked';

/**
 * Convierte Markdown en HTML seguro.
 *
 * <p>Los catalogos estan escritos en Markdown. El HTML que genera `marked` se
 * sanea con DOMPurify antes de inyectarlo: el contenido viene del repositorio
 * (es de confianza), pero el mismo pipe se usa para texto que puede venir de
 * la persona, y no conviene fiarse de ninguna de las dos fuentes.
 *
 * <p>Los resultados se cachean por texto: el contenido de los catalogos es
 * estatico y se renderiza en cada cambio de deteccion.
 *
 * <p>Si el resultado es un unico parrafo, se desenvuelve y se devuelve el
 * contenido sin la etiqueta `<p>`. Asi el mismo pipe sirve para un bloque de
 * Markdown (`<div class="md">`) y para texto que va dentro de un `<h3>` o de
 * un `<button>`, donde un `<p>` no seria HTML valido.
 */
@Pipe({
  name: 'markdown',
})
export class MarkdownPipe implements PipeTransform {
  private readonly sanitizer = inject(DomSanitizer);
  private readonly cache = new Map<string, SafeHtml>();

  transform(value: string | null | undefined): SafeHtml {
    const source = (value ?? '').trim();
    if (!source) {
      return this.sanitizer.bypassSecurityTrustHtml('');
    }

    const cached = this.cache.get(source);
    if (cached) {
      return cached;
    }

    const html = DOMPurify.sanitize(marked.parse(source, { async: false, gfm: true }) as string);
    const safe = this.sanitizer.bypassSecurityTrustHtml(this.unwrapParagraph(html));
    this.cache.set(source, safe);
    return safe;
  }

  /**
   * Quita la etiqueta `<p>` cuando el resultado es un unico parrafo, de modo
   * que el HTML se pueda insertar tambien dentro de un `<h3>` o un `<button>`.
   */
  private unwrapParagraph(html: string): string {
    return html.replace(/^<p>([\s\S]*)<\/p>\s*$/, '$1');
  }
}
