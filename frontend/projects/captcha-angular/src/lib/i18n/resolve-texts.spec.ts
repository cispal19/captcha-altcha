import { interpolate, resolveTexts } from './resolve-texts';

describe('resolveTexts', () => {
  it('usa español por defecto', () => {
    expect(resolveTexts().checkboxLabel).toBe('No soy un robot');
  });

  it("con language:'en' usa los textos en inglés", () => {
    expect(resolveTexts('en').checkboxLabel).toBe("I'm not a robot");
  });

  it('reemplaza solo la clave indicada', () => {
    const texts = resolveTexts('es', { checkboxLabel: 'Soy humano' });
    expect(texts.checkboxLabel).toBe('Soy humano');
    expect(texts.verified).toBe('Verificado');
  });

  it('blocked interpola {minutes}', () => {
    expect(interpolate(resolveTexts().blocked, { minutes: 5 })).toBe(
      'Demasiados intentos. Intente en 5 min',
    );
    expect(interpolate('x {y}', {})).toBe('x {y}');
  });
});
