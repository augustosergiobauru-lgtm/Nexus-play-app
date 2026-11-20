
import assert from 'node:assert/strict';
import {parseM3U,sha256} from './parser.mjs';
import {readFileSync} from 'node:fs';
let passed=0;const test=(name,fn)=>{fn();passed++;console.log('PASS '+name)};
const sample=readFileSync(new URL('../../tests/authorized.m3u',import.meta.url),'utf8');
test('public licensed fixture',()=>assert.equal(parseM3U(sample,'https://host.test/list.m3u').rows[0].content_type,'movie'));
test('BOM CRLF quoted comma and relative URL',()=>{const p=parseM3U('\uFEFF#EXTM3U\r\n#EXTINF:-1 group-title="News, local",Canal, HD\r\nvideo.m3u8','https://host.test/a/list.m3u');assert.equal(p.rows[0].title,'Canal, HD');assert.equal(p.rows[0].group,'News, local');assert.equal(p.rows[0].stream_url,'https://host.test/a/video.m3u8');});
test('live movie series classification',()=>{const p=parseM3U('#EXTM3U\n#EXTINF:-1,News\nhttps://a.test/live/1\n#EXTINF:-1 group-title="Filmes",Cinema\nhttps://a.test/2\n#EXTINF:-1,Show S01E02\nhttps://a.test/3','https://a.test');assert.deepEqual(p.rows.map(r=>r.content_type),['live','movie','series']);});
test('deduplicates URL',()=>{const p=parseM3U(sample+sample.split('\n').slice(1).join('\n'),'https://a.test');assert.equal(p.rows.length,1);assert.equal(p.duplicates,1)});
test('invalid protocols skipped',()=>{const p=parseM3U(sample+'#EXTINF:-1,Bad\nfile:///etc/passwd\n','https://a.test');assert.equal(p.invalid,1)});
test('reject HTML',()=>assert.throws(()=>parseM3U('<html>404</html>','https://a.test'),/INVALID_M3U/));
test('reject HLS media',()=>assert.throws(()=>parseM3U('#EXTM3U\n#EXT-X-TARGETDURATION:10','https://a.test'),/HLS_MANIFEST/));
test('reject empty',()=>assert.throws(()=>parseM3U('#EXTM3U\n','https://a.test'),/EMPTY/));
globalThis.Deno={env:{get:n=>n==='SUPABASE_URL'?'https://db.test':'test-key'},serve:()=>{},resolveDns:async()=>['93.184.216.34']};
const {handle}=await import('./index.mjs');
const request=body=>new Request('https://edge.test',{method:'POST',headers:{Authorization:'Bearer test-session'},body:JSON.stringify(body)});
assert.equal((await handle(new Request('https://edge.test',{method:'POST'}))).status,401);passed++;console.log('PASS missing session');
const originalFetch=globalThis.fetch;
let role='customer',authorized=true,rpcCalls=0;
globalThis.fetch=async(url,opts)=>{
 if(url.includes('/auth/v1/user'))return Response.json({id:'admin-id'});
 if(url.includes('/profiles?'))return Response.json([{role}]);
 if(url.includes('/playlists?')&&opts?.method!=='PATCH')return Response.json([{source_url:'https://public.test/list.m3u'}]);
 if(url.includes('/m3u_authorizations?'))return Response.json(authorized?[{source_url_hash:await sha256('https://public.test/list.m3u')}]:[]);
 if(url==='https://public.test/list.m3u')return new Response(sample);
 if(url.includes('/rpc/')){rpcCalls++;return Response.json({inserted:1,updated:0,replayed:false})}
 return new Response(null,{status:204});
};
const body={playlist_id:'00000000-0000-4000-8000-000000000001'};
assert.equal((await handle(request(body))).status,403);passed++;console.log('PASS customer rejected');
role='owner';authorized=false;assert.equal((await handle(request(body))).status,409);assert.equal(rpcCalls,0);passed++;console.log('PASS unauthorized source rejected');
authorized=true;const result=await handle(request(body));assert.equal(result.status,200);const data=await result.json();assert.equal(data.total,1);assert.equal(data.done,true);assert.equal(rpcCalls,1);passed++;console.log('PASS authorized handler through batch RPC');
assert.equal((await handle(request({...body,offset:1,fingerprint:'changed'}))).status,409);passed++;console.log('PASS source changed rejected');
globalThis.fetch=originalFetch;
console.log(JSON.stringify({passed,rows:parseM3U(sample,'https://public.test/list.m3u').rows,fingerprint:await sha256(sample)}));
