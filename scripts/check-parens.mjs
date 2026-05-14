// Quick paren-balance check for the file given on argv.
import { readFileSync } from 'node:fs';

const path = process.argv[2];
const onlyLine = process.argv[3] ? parseInt(process.argv[3], 10) : null;
const text = readFileSync(path, 'utf8');
const lines = text.split('\n');
let balance = 0;
for (let i = 0; i < lines.length; i++) {
  let line = lines[i];
  line = line.replace(/#"(?:[^"\\]|\\.)*"/g, '#""');
  line = line.replace(/"(?:[^"\\]|\\.)*"/g, '""');
  line = line.replace(/;.*/, '');
  line = line.replace(/\\./g, '');
  for (const c of line) {
    if (c === '(') balance++;
    else if (c === ')') balance--;
  }
  if (onlyLine == null || (i + 1) >= onlyLine - 5 && (i + 1) <= onlyLine + 5) {
    console.log(`line ${i + 1}: balance ${balance}`);
  }
}
