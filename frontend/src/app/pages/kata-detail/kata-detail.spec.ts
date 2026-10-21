import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { of } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { CompletionItem, CompletionResponse, KataDetail } from '../../core/models';
import { KataDetail as KataDetailPage } from './kata-detail';

const KATA = {
  id: 'java-optional-001',
  title: 'Titular',
  track: 'JAVA_CORE',
  difficulty: 'EASY',
  tags: [],
  xp: 10,
  estimatedMinutes: 5,
  prompt: 'Enunciado',
  functionSignature: 'String upper(String s)',
  starterCode:
    'public class Solution {\n    public String upper(String s) {\n        return s;\n    }\n}',
} as unknown as KataDetail;

const ITEMS: CompletionItem[] = [
  {
    label: 'toString()',
    kind: 'method',
    detail: 'String toString()',
    insertText: 'toString()',
    isStatic: false,
    inherited: true,
  },
  {
    label: 'toUpperCase()',
    kind: 'method',
    detail: 'String toUpperCase()',
    insertText: 'toUpperCase()',
    isStatic: false,
    inherited: true,
  },
];

/** Escribe texto en el textarea como lo haria la persona, desde el cursor. */
function type(textarea: HTMLTextAreaElement, text: string, caret: number): void {
  textarea.value = textarea.value.slice(0, caret) + text + textarea.value.slice(caret);
  textarea.selectionStart = caret + text.length;
  textarea.selectionEnd = caret + text.length;
  textarea.dispatchEvent(new Event('input', { bubbles: true }));
}

function press(textarea: HTMLTextAreaElement, key: string): void {
  textarea.dispatchEvent(new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true }));
}

/**
 * Espera a que pase el debounce del autocompletado.
 *
 * <p>El proyecto no lleva zone.js, asi que no hay `fakeAsync`: se esperan los
 * timers de verdad. El debounce son 180 ms y se esperan 250.
 */
function settle(): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, 250));
}

describe('KataDetail · autocompletado', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [KataDetailPage],
      providers: [
        {
          provide: ApiService,
          useValue: {
            getKata: () => of(KATA),
            submit: () => of(null),
            complete: (_id: string, code: string, offset: number) =>
              of<CompletionResponse>({
                items: ITEMS,
                replaceStart: code.lastIndexOf('.', offset) + 1,
                replaceEnd: offset,
              }),
          },
        },
        {
          provide: ActivatedRoute,
          useValue: { paramMap: of(convertToParamMap({ id: KATA.id })) },
        },
      ],
    }).compileComponents();
  });

  it('vuelca el codigo inicial en el textarea', async () => {
    const fixture = TestBed.createComponent(KataDetailPage);
    await fixture.whenStable();
    const textarea = fixture.nativeElement.querySelector('textarea') as HTMLTextAreaElement;
    expect(textarea.value).toBe(KATA.starterCode);
  });

  it('abre el desplegable al escribir un punto', async () => {
    const fixture = TestBed.createComponent(KataDetailPage);
    fixture.detectChanges();
    const textarea = fixture.nativeElement.querySelector('textarea') as HTMLTextAreaElement;
    const caret = KATA.starterCode.indexOf('return s') + 8;

    type(textarea, '.', caret);
    fixture.detectChanges();
    await settle();
    fixture.detectChanges();

    const options = fixture.nativeElement.querySelectorAll('[role="option"]');
    expect(options.length).toBe(2);
    expect(options[0].textContent).toContain('toString()');
    expect(fixture.nativeElement.querySelector('[role="listbox"]')).toBeTruthy();
  });

  it('inserta la sugerencia resaltada al pulsar Enter', async () => {
    const fixture = TestBed.createComponent(KataDetailPage);
    fixture.detectChanges();
    const textarea = fixture.nativeElement.querySelector('textarea') as HTMLTextAreaElement;
    const caret = KATA.starterCode.indexOf('return s') + 8;

    type(textarea, '.', caret);
    fixture.detectChanges();
    await settle();
    fixture.detectChanges();

    press(textarea, 'Enter');
    fixture.detectChanges();

    expect(textarea.value).toContain('return s.toString();');
    expect(textarea.selectionStart).toBe(caret + 1 + 'toString()'.length);
    expect(fixture.nativeElement.querySelector('[role="listbox"]')).toBeNull();
  });

  it('mueve la seleccion con las flechas antes de aceptar', async () => {
    const fixture = TestBed.createComponent(KataDetailPage);
    fixture.detectChanges();
    const textarea = fixture.nativeElement.querySelector('textarea') as HTMLTextAreaElement;
    const caret = KATA.starterCode.indexOf('return s') + 8;

    type(textarea, '.', caret);
    fixture.detectChanges();
    await settle();
    fixture.detectChanges();

    press(textarea, 'ArrowDown');
    fixture.detectChanges();
    const selected = fixture.nativeElement.querySelector('[role="option"][aria-selected="true"]');
    expect(selected?.textContent).toContain('toUpperCase()');

    press(textarea, 'Tab');
    fixture.detectChanges();
    expect(textarea.value).toContain('return s.toUpperCase();');
  });

  it('cierra el desplegable con Escape sin tocar el codigo', async () => {
    const fixture = TestBed.createComponent(KataDetailPage);
    fixture.detectChanges();
    const textarea = fixture.nativeElement.querySelector('textarea') as HTMLTextAreaElement;
    const caret = KATA.starterCode.indexOf('return s') + 8;

    type(textarea, '.', caret);
    fixture.detectChanges();
    await settle();
    fixture.detectChanges();

    press(textarea, 'Escape');
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[role="listbox"]')).toBeNull();
    expect(textarea.value).toBe(
      KATA.starterCode.slice(0, caret) + '.' + KATA.starterCode.slice(caret),
    );
  });

  it('no pide sugerencias fuera de un punto', async () => {
    const fixture = TestBed.createComponent(KataDetailPage);
    fixture.detectChanges();
    const textarea = fixture.nativeElement.querySelector('textarea') as HTMLTextAreaElement;
    const caret = KATA.starterCode.indexOf('return s') + 8;

    type(textarea, ' ', caret);
    fixture.detectChanges();
    await settle();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[role="listbox"]')).toBeNull();
  });

  it('escribe en el editor sin arrastrar el cursor al final del texto', async () => {
    const fixture = TestBed.createComponent(KataDetailPage);
    fixture.detectChanges();
    const textarea = fixture.nativeElement.querySelector('textarea') as HTMLTextAreaElement;
    const caret = KATA.starterCode.indexOf('public String upper') + 6;

    type(textarea, 'X', caret);
    fixture.detectChanges();
    await settle();
    fixture.detectChanges();

    expect(textarea.value.charAt(caret)).toBe('X');
    expect(textarea.selectionStart).toBe(caret + 1);
  });
});
