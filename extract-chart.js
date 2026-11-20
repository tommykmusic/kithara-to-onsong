(() => {
  const page = new URL(location.href);
  if (page.protocol !== 'https:' || !['kithara.to', 'www.kithara.to'].includes(page.hostname) ||
      !/^\/stixoi\/[^/]+(?:\/[^/]+)?\/?$/.test(page.pathname)) {
    return { error: 'Open a song arrangement on Kithara first.' };
  }

  const chart = document.getElementById('text');
  if (!chart) return { error: 'This page does not show a Kithara chord chart yet.' };

  const sourceUrl = page.origin + page.pathname;
  const read = element => element?.innerText ?? element?.textContent ?? '';
  const plain = value => value.normalize('NFC')
    .replace(/[\u00ad\u200b\u200e\u200f\ufeff]/gu, '')
    .replace(/[\u00a0\u202f]/gu, ' ');
  const metadata = value => plain(value).replace(/\s+/gu, ' ').trim()
    .replaceAll('{', '｛').replaceAll('}', '｝');
  const title = metadata(read(document.querySelector('h1.ti')));
  const artist = metadata(read(document.querySelector('h2.ar')));
  if (!title) return { error: 'Kithara did not provide a song title for this arrangement.' };

  const symbols = value => value.replaceAll('♯', '#').replaceAll('♭', 'b');
  const key = Array.from(document.querySelectorAll('details > summary, summary .til'))
    .map(node => symbols(metadata(read(node))).match(
      /^([A-G][#b]?)(?:\s+(μινόρε|ματζόρε|minor|major)(?=\s|$|[:—–])|\s*(m)(?=\s|$)|(?=$))/u
    ))
    .find(Boolean);
  const originalKey = key ? key[1] + (['μινόρε', 'minor', 'm'].includes(key[2] ?? key[3]) ? 'm' : '') : null;

  const meterCues = Array.from(chart.parentElement?.querySelectorAll('table.songtitle tr td > div.til > b') ?? [],
    node => metadata(read(node)))
    .map(note => ({ note, meter: note.match(/(?:^|\()([1-9]\d?\/(?:1|2|4|8|16|32))\)?$/u)?.[1] }))
    .filter(cue => cue.meter);
  const meters = new Set(meterCues.map(cue => cue.meter));

  const segmenter = typeof Intl.Segmenter === 'function'
    ? new Intl.Segmenter('el', { granularity: 'grapheme' })
    : null;
  const graphemes = value => segmenter
    ? Array.from(segmenter.segment(value), item => item.segment)
    : Array.from(value);

  function rowText(node) {
    let output = '';
    let column = 0;
    for (const character of graphemes(plain(read(node)).replace(/[\r\n]+/gu, ' '))) {
      if (character === '\t') {
        const spaces = 8 - column % 8;
        output += ' '.repeat(spaces);
        column += spaces;
      } else {
        output += character;
        column++;
      }
    }
    return output;
  }

  function chordName(value) {
    const name = symbols(value.trim()).replaceAll('−', '-').replaceAll('Δ', 'maj')
      .replaceAll('°', 'dim').replace(/[øØ]7?/gu, 'm7b5');
    if (/^N\.?C\.?$/iu.test(name)) return 'N.C.';
    if (!/^[A-G][#b]{0,2}(?:(?:maj|min|dim|aug|sus|add|omit|alt|no|m|M|[0-9#b+(),-])+)?(?:\/[A-G][#b]{0,2})?$/u.test(name)) {
      return null;
    }
    return name.replace(/^([A-G][#b]?)min(?=\d|$)/u, '$1m')
      .replace(/^([A-G][#b]?)M(?=\d|$)/u, '$1maj')
      .replace(/^([A-G][#b]?)-(?=\d|$)/u, '$1m');
  }

  function positionedChords(row) {
    const text = rowText(row);
    const found = [];
    let from = 0;
    for (const anchor of row.querySelectorAll('a.clickPlay')) {
      const label = rowText(anchor).trim();
      if (!label) continue;
      const start = text.indexOf(label, from);
      if (start === -1) continue;
      found.push({ start, end: start + label.length, name: chordName(label) });
      from = start + label.length;
    }
    if (!found.length) {
      for (const token of text.matchAll(/[^\s|]+/gu)) {
        const name = chordName(token[0]);
        if (name) found.push({ start: token.index, end: token.index + token[0].length, name });
      }
    }
    return { text, chords: found.filter(item => item.name) };
  }

  const literal = value => value.replaceAll('[', '［').replaceAll(']', '］')
    .replaceAll('{', '｛').replaceAll('}', '｝')
    .replace(/^#/u, '＃').replace(/\\$/u, '＼');

  function addInlineChords(lyric, chordRow) {
    const letters = graphemes(literal(lyric.trimEnd()));
    let offset = 0;
    let result = '';
    for (const chord of chordRow.chords) {
      const column = graphemes(chordRow.text.slice(0, chord.start)).length;
      result += letters.slice(offset, column).join('') + `[${chord.name}]`;
      offset = column;
    }
    return result + letters.slice(offset).join('');
  }

  function chordOnlyLine({ text, chords }) {
    let offset = 0;
    let result = '';
    for (const chord of chords) {
      result += literal(text.slice(offset, chord.start)) + `[${chord.name}]`;
      offset = chord.end;
    }
    return (result + literal(text.slice(offset))).trim();
  }

  function sectionLabel(value) {
    const label = metadata(value).replace(/^[\[({]+|[\])}:]+$/gu, '').trim();
    const match = label.normalize('NFD').replace(/\p{M}/gu, '').toLocaleLowerCase('el')
      .match(/^(verse|chorus|bridge|intro|outro|instrumental|solo|κουπλε|στροφη|ρεφρεν|γεφυρα|εισαγωγη|ιντρο|φιναλε|οργανικο|σολο)(?:\s+\d+)?$/u);
    return match ? label : null;
  }

  const header = [`{title: ${title}}`];
  if (artist) header.push(`{artist: ${artist}}`);
  if (originalKey) header.push(`{key: ${originalKey}}`);
  if (meters.size === 1) header.push(`{time: ${[...meters][0]}}`);
  header.push(`# Source: ${sourceUrl}`);

  const rows = Array.from(chart.children);
  const body = [...new Set(meterCues.map(cue => `{comment: ${cue.note}}`))];
  if (body.length) body.push('');
  let lyricLines = 0;
  let chordCount = 0;

  for (let index = 0; index < rows.length; index++) {
    const row = rows[index];
    const text = rowText(row);
    if (row.classList.contains('no')) {
      if (body.length && body[body.length - 1] !== '') body.push('');
      continue;
    }

    const label = sectionLabel(text);
    if (label) {
      if (body.length && body[body.length - 1] !== '') body.push('');
      body.push(`${label}:`);
    } else if (row.classList.contains('ch')) {
      const parsed = positionedChords(row);
      const next = rows[index + 1];
      const lyric = next?.classList.contains('te') && rowText(next);
      if (lyric && !sectionLabel(lyric) && parsed.chords.length) {
        body.push(addInlineChords(lyric, parsed));
        lyricLines++;
        index++;
      } else if (text.trim()) {
        body.push(chordOnlyLine(parsed));
      }
      chordCount += parsed.chords.length;
    } else if (row.classList.contains('te') && text.trim()) {
      body.push(literal(text.trimEnd()));
      lyricLines++;
    } else if (row.matches('.co, .cm, .comment') && text.trim()) {
      body.push(`{comment: ${metadata(text)}}`);
    }
  }

  if (!lyricLines && !chordCount) {
    return { error: 'No lyrics or playable chords were found in this arrangement.' };
  }

  return {
    title,
    artist,
    key: originalKey,
    chordpro: `${header.join('\n')}\n\n${body.join('\n').trimEnd()}\n`,
    sourceUrl,
    lyricLines,
    chordCount,
  };
})();
