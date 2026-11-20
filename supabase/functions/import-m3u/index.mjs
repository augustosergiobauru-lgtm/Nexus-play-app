
import {parseM3U,sha256} from './parser.mjs';
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
  const source=list[0].source_url;
  const approvals=await api('/rest/v1/m3u_authorizations?select=source_url_hash&playlist_id=eq.'+playlistId);
  if(approvals[0]?.source_url_hash!==await sha256(source))return reply(409,{error:'SOURCE_NOT_AUTHORIZED'});
  const url=new URL(source);
  if(url.protocol!=='https:'||url.username||url.password)return reply(422,{error:'HTTPS_SOURCE_REQUIRED'});
  // DNS resolution prevents fetching local/private hosts. Redirects are refused.
  const addresses=[...await Deno.resolveDns(url.hostname,'A').catch(()=>[]),...await Deno.resolveDns(url.hostname,'AAAA').catch(()=>[])];
  const privateIP=ip=>ip.includes(':')?/^(::|fc|fd|fe80|ff)/i.test(ip)||ip.includes('.'): /^(0\.|10\.|127\.|169\.254\.|172\.(1[6-9]|2\d|3[01])\.|192\.168\.|100\.(6[4-9]|[7-9]\d|1[01]\d|12[0-7])\.|22[4-9]\.|23\d\.|24\d\.|25[0-5]\.)/.test(ip);
  if(!addresses.length||addresses.some(privateIP))return reply(422,{error:'PUBLIC_SOURCE_REQUIRED'});
  const sourceResponse=await fetch(source,{redirect:'manual',signal:AbortSignal.timeout(20000)});
  if(!sourceResponse.ok)throw Error('SOURCE_HTTP_'+sourceResponse.status);
  const reader=sourceResponse.body.getReader(),chunks=[];let size=0;
  while(true){const part=await reader.read();if(part.done)break;size+=part.value.length;if(size>16*1024*1024){await reader.cancel();throw Error('SOURCE_TOO_LARGE');}chunks.push(part.value);}
  const bytes=new Uint8Array(size);let pos=0;for(const c of chunks){bytes.set(c,pos);pos+=c.length;}
  const text=new TextDecoder().decode(bytes),fingerprint=await sha256(text);
  if(offset>0&&body.fingerprint!==fingerprint)return reply(409,{error:'SOURCE_CHANGED_RESTART'});
  const parsed=parseM3U(text,source);
  if(offset>=parsed.rows.length)return reply(400,{error:'OFFSET_OUT_OF_RANGE'});
  const batch=parsed.rows.slice(offset,offset+limit),next=offset+batch.length,done=next===parsed.rows.length;
  const result=await api('/rest/v1/rpc/import_m3u_batch',{method:'POST',body:JSON.stringify({p_playlist_id:playlistId,p_fingerprint:fingerprint,p_offset:offset,p_items:batch,p_done:done})});
  return reply(200,{...result,total:parsed.rows.length,processed:batch.length,invalid:parsed.invalid,duplicates:parsed.duplicates,fingerprint,next_offset:done?null:next,done});
 }catch(e){
  const known=/^(SOURCE_HTTP_\d+|DATABASE_HTTP_\d+|SOURCE_TOO_LARGE|INVALID_M3U|EMPTY_M3U|HLS_MANIFEST_NOT_CATALOG)$/;
  const code=known.test(e.message)?e.message:'IMPORT_FAILED';
  if(playlistId)try{await api('/rest/v1/playlists?id=eq.'+playlistId,{method:'PATCH',body:JSON.stringify({status:'error',last_error:code,last_check_at:new Date().toISOString()})});}catch{}
  return reply(422,{error:code});
 }
}
Deno.serve(handle);
