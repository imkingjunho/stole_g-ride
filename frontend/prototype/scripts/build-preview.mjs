import { readFile, writeFile } from 'node:fs/promises';
import { resolve, basename } from 'node:path';

// 번들에 네트워크 의존성이 없어 파일을 더블클릭해도 동작하는 단일 HTML을 만든다.
const dist = resolve('dist');
let html = await readFile(resolve(dist, 'index.html'), 'utf8');
const scripts = [...html.matchAll(/<script\b[^>]*src="([^"]+)"[^>]*><\/script>/g)];
for (const [tag, src] of scripts) {
  const js = await readFile(resolve(dist, 'assets', basename(src)), 'utf8');
  html = html.replace(
    tag,
    () => `<script type="module">${js.replace(/<\/script/gi, '<\\/script')}</script>`,
  );
}
const styles = [...html.matchAll(/<link\b[^>]*rel="stylesheet"[^>]*href="([^"]+)"[^>]*>/g)];
for (const [tag, href] of styles) {
  const css = await readFile(resolve(dist, 'assets', basename(href)), 'utf8');
  html = html.replace(tag, () => `<style>${css}</style>`);
}
await writeFile(resolve(dist, 'WE-Meet-Preview.html'), html);
console.log('단일 파일 미리보기 생성: dist/WE-Meet-Preview.html');
