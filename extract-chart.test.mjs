import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import vm from 'node:vm';

const script = await readFile(new URL('./extract-chart.js', import.meta.url), 'utf8');
const songUrl = 'https://kithara.to/stixoi/12345/test-song';

function element(className, innerText, chordLabels = []) {
  return {
    innerText,
    classList: { contains: name => name === className },
    matches: selectors => selectors.split(', ').includes(`.${className}`),
    querySelectorAll: () => chordLabels.map(innerText => ({ innerText })),
  };
}

function extract({ url = songUrl, rows, summary = '', meterNotes = [] }) {
  const document = {
    getElementById: () => rows ? {
      children: rows,
      parentElement: { querySelectorAll: () => meterNotes.map(innerText => ({ innerText })) },
    } : null,
    querySelector: selector => ({ innerText: selector === 'h1.ti' ? 'Δοκιμή' : 'Άννα' }),
    querySelectorAll: () => summary ? [{ innerText: summary }] : [],
  };
  return vm.runInNewContext(script, { URL, document, location: { href: url } });
}

test('imports Greek lyrics, explicit key and meter, and positioned chords', () => {
  const parsed = extract({
    url: `${songUrl}?example=ignored`,
    summary: 'G μινόρε Σύντομος οδηγός',
    meterNotes: ['ζεϊμπέκικο (9/4)'],
    rows: [
      element('te', '[Εισαγωγή]'),
      element('chMono', 'Gm | Cø7'),
      element('ch', 'Gm | Cø7', ['Gm', 'Cø7']),
      element('ch', 'Gm D♭/F', ['Gm', 'D♭/F']),
      element('no', ''),
      element('te', 'Κουπλέ 1:'),
      element('ch', 'Gm\u00a0  D♭/F', ['Gm', 'D♭/F']),
      element('te', 'Νέο κείμενο'),
      element('te', 'Απλή συνέχεια'),
      element('no', ''),
      element('sidebar', 'Sidebar text'),
    ],
  });

  assert.equal(parsed.key, 'Gm');
  assert.equal(parsed.sourceUrl, songUrl);
  assert.equal(parsed.lyricLines, 2);
  assert.equal(parsed.chordCount, 6);
  assert.equal(parsed.chordpro, [
    '{title: Δοκιμή}',
    '{artist: Άννα}',
    '{key: Gm}',
    '{time: 9/4}',
    `# Source: ${songUrl}`,
    '',
    '{comment: ζεϊμπέκικο (9/4)}',
    '',
    'Εισαγωγή:',
    '[Gm] | [Cm7b5]',
    '[Gm] [Db/F]',
    '',
    'Κουπλέ 1:',
    '[Gm]Νέο κ[Db/F]είμενο',
    'Απλή συνέχεια',
    '',
  ].join('\n'));
});

test('omits unknown music metadata and imports unlinked ASCII chord tokens', () => {
  const parsed = extract({
    summary: 'A quick guide',
    meterNotes: ['Χωρίς ένδειξη', 'φιγούρα (3/5)'],
    rows: [element('ch', '  C7   B♭'), element('te', 'Μία γραμμή')],
  });
  assert.equal(parsed.key, null);
  assert.equal(parsed.chordCount, 2);
  assert.match(parsed.chordpro, /Μί\[C7\]α γρα\[Bb\]μμή/u);
  assert.doesNotMatch(parsed.chordpro, /\{(?:key|time|tempo):/u);
});

test('preserves conflicting meter cues without assigning a global meter', () => {
  const parsed = extract({
    meterNotes: ['Πρώτο μέρος (7/8)', 'Δεύτερο μέρος (2/4)'],
    rows: [element('te', 'Μία γραμμή')],
  });
  assert.doesNotMatch(parsed.chordpro, /\{time:/u);
  assert.match(parsed.chordpro, /\{comment: Πρώτο μέρος \(7\/8\)\}/u);
  assert.match(parsed.chordpro, /\{comment: Δεύτερο μέρος \(2\/4\)\}/u);
});

test('rejects pages outside an arrangement and incomplete charts', () => {
  assert.match(extract({ url: 'https://kithara.to/' }).error, /arrangement/u);
  assert.match(extract({ url: 'https://kithara.to.example/stixoi/123' }).error, /arrangement/u);
  assert.match(extract({}).error, /does not show/u);
  assert.match(extract({ rows: [element('no', '')] }).error, /No lyrics/u);
});
