
import {parseM3U,sha256} from './parser.mjs';
import {serverURL,readSource,loadXtream} from './source.mjs';
const headers={'Content-Type':'application/json','Access-Control-Allow-Origin':'*','Access-Control-Allow-Headers':'authorization,apikey,content-type','Access-Control-Allow-Methods':'POST,OPTIONS'};
const reply=(status,data)=>new Response(JSON.stringify(data),{status,headers});
export async function handle(req){
 if(req.method==='OPTIONS')return reply(200,{ok:true});
 if(req.method!=='POST')return reply(405,{error:'POST_REQUIRED'});
 const root=Deno.env.get('SUPABASE_URL'),key=Deno.env.get('SUPABASE_SERVICE_ROLE_KEY');
 const auth=req.headers.get('Authorization');
 if(!auth?.startsWith('Bearer '))return reply(401,{error:'AUTH_REQUIRED'});
 async function api(path,options={}){
  const r=await fetch(root+path,{...options,headers:{apikey:key,Authorization:'Bearer '+key,'Content-Type':'application/json',...options.headers},signal:AbortSignal.timeout(15000)});
  if(!r.ok)throw Error('DATABASE_HTTP_'+r.status);
  return r.status===204?null:r.json();
 }
 let playlistId;
 try{
  const userResponse=await fetch(root+'/auth/v1/user',{headers:{apikey:key,Authorization:auth},signal:AbortSignal.timeout(10000)});
  if(!userResponse.ok)return reply(401,{error:'INVALID_SESSION'});
  const user=await userResponse.json();
  const profile=await api('/rest/v1/profiles?select=role&id=eq.'+encodeURIComponent(user.id));
  if(profile[0]?.role!=='owner')return reply(403,{error:'ADMIN_REQUIRED'});
  if(Number(req.headers.get('content-length')||0)>4096)return reply(413,{error:'REQUEST_TOO_LARGE'});
  const raw=await req.text();if(raw.length>4096)return reply(413,{error:'REQUEST_TOO_LARGE'});
  const body=JSON.parse(raw),offset=body.offset??0,limit=body.limit??200;
  if(!/^[0-9a-f-]{36}$/i.test(body.playlist_id)||!Number.isInteger(offset)||offset<0||!Number.isInteger(limit)||limit<1||limit>200)return reply(400,{error:'INVALID_PARAMETERS'});
  playlistId=body.playlist_id;
  const list=await api('/rest/v1/playlists?select=id,source_url&id=eq.'+playlistId);
  if(!list.length)return reply(404,{error:'PLAYLIST_NOT_FOUND'});
  if(body.action==='configure_xtream'){
   const config=body.xtream;
   if(!config||typeof config.username!=='string'||typeof config.password!=='string'||!config.username||!config.password||typeof body.authorization_reference!=='string'||body.authorization_reference.length<12)return reply(400,{error:'INVALID_XTREAM_CONFIGURATION'});
   const configuredSource=serverURL(config.server,config.allow_insecure_http===true).href;
   await api('/rest/v1/playlists?id=eq.'+playlistId,{method:'PATCH',body:JSON.stringify({source_url:configuredSource,status:'draft',last_error:null})});
   await api('/rest/v1/m3u_xtream_sources?on_conflict=playlist_id',{method:'POST',headers:{Prefer:'resolution=merge-duplicates'},body:JSON.stringify({playlist_id:playlistId,source_url:configuredSource,username:config.username,password:config.password,allow_insecure_http:config.allow_insecure_http===true,updated_at:new Date().toISOString()})});
   await api('/rest/v1/m3u_authorizations?on_conflict=playlist_id',{method:'POST',headers:{Prefer:'resolution=merge-duplicates'},body:JSON.stringify({playlist_id:playlistId,source_url_hash:await sha256(configuredSource),evidence:body.authorization_reference,approved_by:user.id,approved_at:new Date().toISOString()})});
   return reply(200,{configured:true,source_kind:'xtream'});
  }
  const source=list[0].source_url;
  const approvals=await api('/rest/v1/m3u_authorizations?select=source_url_hash&playlist_id=eq.'+playlistId);
  if(approvals[0]?.source_url_hash!==await sha256(source))return reply(409,{error:'SOURCE_NOT_AUTHORIZED'});
  const configs=await api('/rest/v1/m3u_xtream_sources?select=source_url,username,password,allow_insecure_http&playlist_id=eq.'+playlistId);
  const config=configs[0];
  if(config&&config.source_url!==source)return reply(409,{error:'SOURCE_CONFIGURATION_CHANGED'});
  const url=new URL(source);
  if(url.username||url.password||!['https:','http:'].includes(url.protocol)||url.protocol==='http:'&&!config?.allow_insecure_http)return reply(422,{error:'HTTP_REQUIRES_EXPLICIT_APPROVAL'});
  // DNS resolution prevents fetching local/private hosts. Redirects are refused.
  const addresses=[...await Deno.resolveDns(url.hostname,'A').catch(()=>[]),...await Deno.resolveDns(url.hostname,'AAAA').catch(()=>[])];
  const privateIP=ip=>ip.includes(':')?/^(::|fc|fd|fe80|ff)/i.test(ip)||ip.includes('.'): /^(0\.|10\.|127\.|169\.254\.|172\.(1[6-9]|2\d|3[01])\.|192\.168\.|100\.(6[4-9]|[7-9]\d|1[01]\d|12[0-7])\.|22[4-9]\.|23\d\.|24\d\.|25[0-5]\.)/.test(ip);
  if(!addresses.length||addresses.some(privateIP))return reply(422,{error:'PUBLIC_SOURCE_REQUIRED'});

  let parsed,fingerprint;
  if(config){
   parsed=await loadXtream(source,config,body.section??'live',body.series_index??0,body.category_index??0);
   fingerprint=parsed.fingerprint;
  }else{
   const text=await readSource(source);
   parsed=parseM3U(text,source);
   fingerprint=await sha256(text);
  }
  if(offset>0&&body.fingerprint!==fingerprint)return reply(409,{error:'SOURCE_CHANGED_RESTART'});
  if(offset>parsed.rows.length||offset===parsed.rows.length&&parsed.rows.length>0)return reply(400,{error:'OFFSET_OUT_OF_RANGE'});
  const batch=parsed.rows.slice(offset,offset+limit),next=offset+batch.length,pageDone=next===parsed.rows.length;
  const done=pageDone&&(!config||parsed.nextSection===null);
  let result={inserted:0,updated:0,replayed:false};
  if(batch.length)result=await api('/rest/v1/rpc/import_m3u_batch',{method:'POST',body:JSON.stringify({p_playlist_id:playlistId,p_fingerprint:fingerprint,p_offset:offset,p_items:batch,p_done:done})});
  else if(done)await api('/rest/v1/playlists?id=eq.'+playlistId,{method:'PATCH',body:JSON.stringify({status:'active',last_import_at:new Date().toISOString(),last_check_at:new Date().toISOString(),health_status:'online',last_error:null})});
  return reply(200,{...result,source_kind:config?'xtream':'m3u',total:parsed.rows.length,processed:batch.length,invalid:parsed.invalid,duplicates:parsed.duplicates,fingerprint,next_offset:pageDone?null:next,section:config?parsed.section:null,series_index:config?parsed.seriesIndex:null,next_section:config?(pageDone?parsed.nextSection:parsed.section):null,next_series_index:config?(pageDone?parsed.nextSeriesIndex:parsed.seriesIndex):null,category_index:config?parsed.categoryIndex:null,next_category_index:config?(pageDone?parsed.nextCategoryIndex:parsed.categoryIndex):null,done});
 }catch(e){
  const known=/^(SOURCE_HTTP_\d+|DATABASE_HTTP_\d+|SOURCE_TOO_LARGE|INVALID_M3U|EMPTY_M3U|HLS_MANIFEST_NOT_CATALOG|XTREAM_[A-Z_]+|INVALID_XTREAM_[A-Z_]+|HTTP_REQUIRES_EXPLICIT_APPROVAL)$/;
  const code=known.test(e.message)?e.message:'IMPORT_FAILED';
  if(playlistId)try{await api('/rest/v1/playlists?id=eq.'+playlistId,{method:'PATCH',body:JSON.stringify({status:'error',last_error:code,last_check_at:new Date().toISOString()})});}catch{}
  return reply(422,{error:code});
 }
}
Deno.serve(handle);
