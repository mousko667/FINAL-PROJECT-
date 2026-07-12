// Validation WCAG AA des combinaisons texte-sur-fond du lot couleur.
// Zéro dépendance : conversion HSL->sRGB->luminance relative + ratio de contraste.
// Les entrées sont les MÊMES valeurs HSL que les tokens d'index.css (source de vérité).

function hslToRgb(h, s, l) {
  s /= 100; l /= 100;
  const k = (n) => (n + h / 30) % 12;
  const a = s * Math.min(l, 1 - l);
  const f = (n) => l - a * Math.max(-1, Math.min(k(n) - 3, Math.min(9 - k(n), 1)));
  return [f(0), f(8), f(4)].map((x) => Math.round(x * 255));
}
// Compose une couleur semi-transparente sur un fond opaque (alpha compositing).
function over(fg, bg, alpha) {
  return fg.map((c, i) => Math.round(c * alpha + bg[i] * (1 - alpha)));
}
function relLum([r, g, b]) {
  const f = (c) => {
    c /= 255;
    return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
  };
  return 0.2126 * f(r) + 0.7152 * f(g) + 0.0722 * f(b);
}
function ratio(a, b) {
  const [la, lb] = [relLum(a), relLum(b)].sort((x, y) => y - x);
  return (la + 0.05) / (lb + 0.05);
}

// --- Tokens (HSL triplets identiques à index.css) ---
const H = {
  octNavy: [213, 64, 16], octNavyLight: [213, 50, 22],
  primaryDark: [213, 45, 42],
  info: [206, 51, 37], infoDark: [207, 56, 64],
  ink: [40, 15, 12], inkDark: [43, 13, 90],
  groundLight: [45, 33, 98], surfaceLight: [0, 0, 100],
  groundDark: [218, 18, 9], surfaceDark: [218, 19, 11],
  goldDeep: [44, 54, 39], goldDeepDark: [43, 62, 61],
  white: [0, 0, 100],
};
const rgb = (t) => hslToRgb(...t);

// Combinaisons à valider : [nom, texte, fond, [alphaFondSurBase, base]?, seuil]
const checks = [
  // En-tête : texte blanc sur dégradé navy (light) — pire cas = extrémité claire navy-light
  ['header text/white on navy-light (light)', rgb(H.white), rgb(H.octNavyLight), null, 4.5],
  // En-tête dark : encre claire sur navy éclaircie (--primary dark)
  ['header text/ink-dark on primary-dark (dark)', rgb(H.inkDark), rgb(H.primaryDark), null, 4.5],
  // page-tint light : ink sur info@0.07 composé sur ground
  ['ink on page-tint (light)', rgb(H.ink), over(rgb(H.info), rgb(H.groundLight), 0.07), null, 4.5],
  ['ink-dark on page-tint (dark)', rgb(H.inkDark), over(rgb(H.infoDark), rgb(H.groundDark), 0.06), null, 4.5],
  // nav-tint light : ink sur info@0.12 composé sur ground
  ['ink on nav-tint (light)', rgb(H.ink), over(rgb(H.info), rgb(H.groundLight), 0.12), null, 4.5],
  ['ink-dark on nav-tint (dark)', rgb(H.inkDark), over(rgb(H.infoDark), rgb(H.groundDark), 0.14), null, 4.5],
  // kpi-info light : ink sur info@0.09 composé sur ground
  ['ink on kpi-info (light)', rgb(H.ink), over(rgb(H.info), rgb(H.groundLight), 0.09), null, 4.5],
  ['ink-dark on kpi-info (dark)', rgb(H.inkDark), over(rgb(H.infoDark), rgb(H.groundDark), 0.10), null, 4.5],
  // bouton secondary : texte info sur surface (light) et sur surface dark
  ['info text on surface (light)', rgb(H.info), rgb(H.surfaceLight), null, 4.5],
  ['info-dark text on surface (dark)', rgb(H.infoDark), rgb(H.surfaceDark), null, 4.5],
  // bouton gold : texte navy-dark sur gold-deep (déjà en usage, on revérifie)
  ['navy-dark on gold-deep (light)', rgb([213, 70, 10]), rgb(H.goldDeep), null, 4.5],
];

let failed = 0;
for (const [name, fg, bg, , threshold] of checks) {
  const r = ratio(fg, bg);
  const ok = r >= threshold;
  if (!ok) failed++;
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${r.toFixed(2)}:1  (min ${threshold})  ${name}`);
}
console.log(failed === 0 ? '\nALL CHECKS PASS' : `\n${failed} CHECK(S) FAILED`);
process.exit(failed === 0 ? 0 : 1);
