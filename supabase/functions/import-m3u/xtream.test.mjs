
import assert from 'node:assert/strict';
import {serverURL,xtreamURL,xtreamRows,loadXtream} from './source.mjs';
const credentials={username:'test+user',password:'test/p&ss',allow_insecure_http:false};
const server=serverURL('https://provider.test');
let passed=0;
const test=(name,fn)=>{fn();passed++;console.log('PASS '+name)};
test('normalize server',()=>assert.equal(server.href,'https://provider.test/'));
test('credentials URL encoding',()=>{const u=xtreamURL(server,credentials,'get_live_streams');assert.equal(u.searchParams.get('password'),credentials.password);assert.equal(u.searchParams.get('username'),credentials.username)});
test('HTTP requires explicit approval',()=>assert.throws(()=>serverURL('http://provider.test'),/HTTP_REQUIRES/));
test('explicit HTTP preserved',()=>assert.equal(serverURL('http://provider.test',true).protocol,'http:'));
test('HTTPS port 80 preserved without downgrade',()=>assert.equal(serverURL('https://provider.test:80').port,'80'));
test('embedded URL credentials rejected',()=>assert.throws(()=>serverURL('https://u:p@provider.test'),/INVALID/));
test('number and string IDs tolerated',()=>{const p=xtreamRows([{stream_id:1,name:'Live',category_id:7}],[{category_id:'7',category_name:'News'}],'live',server,credentials);assert.equal(p.rows[0].group,'News');assert.equal(p.rows[0].content_type,'live');assert.ok(p.rows[0].stream_url.includes('test%2Fp%26ss'))});
test('deduplicate ID',()=>assert.equal(xtreamRows([{stream_id:1,name:'a'},{stream_id:'1',name:'a'}],[],'live',server,credentials).duplicates,1));
test('reject invalid extensions',()=>assert.equal(xtreamRows([{stream_id:1,name:'a',container_extension:'../bad'}],[],'movie',server,credentials).invalid,1));
const realFetch=globalThis.fetch;
let rejectLogin=false,emptySeries=false;
globalThis.fetch=async input=>{
 const url=new URL(input),action=url.searchParams.get('action');
 if(!action)return Response.json({user_info:{auth:rejectLogin?0:'1',status:'Active'}});
 if(action.endsWith('_categories'))return Response.json([{category_id:7,category_name:'Category'}]);
 if(action==='get_live_streams')return Response.json([{stream_id:12,name:'Live',category_id:'7'}]);
 if(action==='get_vod_streams')return Response.json([{stream_id:'22',name:'Movie',container_extension:'mkv',category_id:7}]);
 if(action==='get_series')return Response.json(emptySeries?[]:[{series_id:10,name:'Show',category_id:7},{series_id:2,name:'Other show',category_id:7}]);
 if(action==='get_series_info')return Response.json({episodes:{1:[{id:31,title:'S01E01',container_extension:'mp4'}]}});
 throw Error('UNEXPECTED_TEST_ACTION');
};
const live=await loadXtream(server.href,credentials,'live');assert.equal(live.rows[0].content_type,'live');assert.equal(live.nextSection,'movie');passed++;console.log('PASS live API catalog');
const movie=await loadXtream(server.href,credentials,'movie');assert.ok(movie.rows[0].stream_url.endsWith('/22.mkv'));assert.equal(movie.nextSection,'series');passed++;console.log('PASS movie API catalog');
const series=await loadXtream(server.href,credentials,'series',0);assert.equal(series.rows[0].content_type,'series');assert.equal(series.nextSeriesIndex,1);assert.equal(series.nextSection,'series');passed++;console.log('PASS series episode pagination');
const last=await loadXtream(server.href,credentials,'series',1);assert.equal(last.nextSection,null);passed++;console.log('PASS last series completes');
emptySeries=true;const empty=await loadXtream(server.href,credentials,'series',0);assert.equal(empty.rows.length,0);assert.equal(empty.nextSection,null);passed++;console.log('PASS empty series catalog');
rejectLogin=true;await assert.rejects(()=>loadXtream(server.href,credentials,'live'),/XTREAM_LOGIN_REJECTED/);passed++;console.log('PASS rejected login');


rejectLogin=false;
const categoryFetch=globalThis.fetch,selectedCategories=[];
globalThis.fetch=async input=>{
 const u=new URL(input),a=u.searchParams.get('action');
 if(a==='get_vod_categories')return Response.json([{category_id:9,category_name:'B'},{category_id:2,category_name:'A'}]);
 if(a==='get_vod_streams')selectedCategories.push(u.searchParams.get('category_id'));
 return categoryFetch(input);
};
const firstCategory=await loadXtream(server.href,credentials,'movie',0,0);
assert.equal(firstCategory.nextSection,'movie');assert.equal(firstCategory.nextCategoryIndex,1);passed++;console.log('PASS large movie catalog starts with bounded category');
const lastCategory=await loadXtream(server.href,credentials,'movie',0,1);
assert.equal(lastCategory.nextSection,'series');assert.equal(lastCategory.nextCategoryIndex,0);assert.deepEqual(selectedCategories,['2','9']);passed++;console.log('PASS movie category cursor preserves sorted categories');
globalThis.fetch=categoryFetch;

const providerFetch=globalThis.fetch;
globalThis.Deno={env:{get:n=>n==='SUPABASE_URL'?'https://db.test':'test-key'},serve:()=>{},resolveDns:async()=>['93.184.216.34']};
const {sha256}=await import('./parser.mjs');
const {handle}=await import('./index.mjs');
let calls=[],providerCalls=0;
rejectLogin=false;emptySeries=false;
globalThis.fetch=async(input,options={})=>{
 const url=String(input);
 if(url.startsWith('https://db.test')){
  if(url.includes('/auth/v1/user'))return Response.json({id:'admin-test'});
  if(url.includes('/profiles?'))return Response.json([{role:'owner'}]);
  if(url.includes('/playlists?')&&options.method!=='PATCH')return Response.json([{source_url:server.href}]);
  if(url.includes('/m3u_xtream_sources?')&&options.method!=='POST')return Response.json([{...credentials,source_url:server.href}]);
  if(url.includes('/m3u_authorizations?')&&options.method!=='POST')return Response.json([{source_url_hash:await sha256(server.href)}]);
  if(url.includes('/rpc/')){calls.push(JSON.parse(options.body));return Response.json({inserted:1,updated:0,replayed:false})}
  return new Response(null,{status:204});
 }
 providerCalls++;return providerFetch(input,options);
};
const request=body=>new Request('https://edge.test',{method:'POST',headers:{Authorization:'Bearer test-session'},body:JSON.stringify({playlist_id:'00000000-0000-4000-8000-000000000001',...body})});
const result=await handle(request({section:'live'})),data=await result.json();
assert.equal(result.status,200);assert.equal(data.done,false);assert.equal(data.next_section,'movie');assert.equal(calls[0].p_done,false);assert.equal(data.processed,1);assert.ok(!JSON.stringify(data).includes(credentials.password));passed++;console.log('PASS Xtream authenticated handler to batch RPC without credential response');
const finalResult=await handle(request({section:'series',series_index:1}));
assert.equal(finalResult.status,200);assert.equal((await finalResult.json()).done,true);assert.equal(calls.at(-1).p_done,true);passed++;console.log('PASS last series handler finalizes import');
const before=providerCalls;
const configResult=await handle(request({action:'configure_xtream',xtream:{server:server.href,...credentials},authorization_reference:'test administrator authorization'}));
assert.equal(configResult.status,200);assert.equal(providerCalls,before);assert.ok(!(await configResult.text()).includes(credentials.password));passed++;console.log('PASS admin configuration stores privately without calling provider');
globalThis.fetch=realFetch;
console.log(JSON.stringify({total_passed:passed}));



const posterRows=xtreamRows([{id:42,title:'Show S01E01',_series_cover:'https://images.test/show.jpg',info:{movie_image:'https://images.test/episode.jpg'}}],[],'series',server,credentials);
assert.equal(posterRows.rows[0].logo_url,'https://images.test/show.jpg');
console.log('PASS series artwork uses show poster instead of episode still');
