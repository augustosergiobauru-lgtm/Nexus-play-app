
export function parseM3U(text,base) {
 if(!text.replace(/^\uFEFF/,'').trimStart().startsWith('#EXTM3U')) throw Error('INVALID_M3U');
 if(/^#EXT-X-(TARGETDURATION|STREAM-INF|MEDIA-SEQUENCE):/m.test(text)) throw Error('HLS_MANIFEST_NOT_CATALOG');
 let meta=null,group='',invalid=0,duplicates=0; const rows=[],seen=new Set();
 for(const raw of text.split(/\r?\n/)){
  const line=raw.trim(); if(!line)continue;
  if(line.startsWith('#EXTINF:')){
   if(meta)invalid++;
   let quoted=false,comma=-1;
   for(let i=8;i<line.length;i++){if(line[i]==='"')quoted=!quoted;if(line[i]===','&&!quoted){comma=i;break;}}
   const attrs={}; for(const m of line.slice(0,comma<0?undefined:comma).matchAll(/([\w-]+)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s,]+))/g)) attrs[m[1]]=m[2]??m[3]??m[4];
   meta={title:comma<0?'':line.slice(comma+1).trim(),attrs};group=attrs['group-title']||'';continue;
  }
  if(line.startsWith('#EXTGRP:')){group=line.slice(8).trim();continue;}
  if(line.startsWith('#'))continue;
  if(!meta){invalid++;continue;}
  try{
   const url=new URL(line,base); if(!['http:','https:'].includes(url.protocol)||!meta.title)throw Error();
   const stream_url=url.href; if(seen.has(stream_url)){duplicates++;meta=null;continue;} seen.add(stream_url);
   const hint=(group+' '+meta.title+' '+url.pathname).normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLowerCase();
   const content_type=/\bseries?\b|\bs\d{1,2}\s?e\d{1,3}\b|\b\d{1,2}x\d{1,3}\b/.test(hint)?'series':/\b(movie|movies|filme|filmes|vod)\b|\.(mp4|mkv|avi)$/.test(hint)?'movie':'live';
   let logo_url=null;try{const logo=new URL(meta.attrs['tvg-logo']);if(['http:','https:'].includes(logo.protocol))logo_url=logo.href;}catch{}
   rows.push({title:meta.title,stream_url,group:group||'Sem categoria',content_type,external_id:meta.attrs['tvg-id']||null,logo_url});
  }catch{invalid++;}
  meta=null;
 }
 if(meta)invalid++;
 if(!rows.length)throw Error('EMPTY_M3U');
 return {rows,invalid,duplicates};
}
export async function sha256(text){return Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',new TextEncoder().encode(text))),v=>v.toString(16).padStart(2,'0')).join('');}
