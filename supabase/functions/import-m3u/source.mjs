
import {sha256} from './parser.mjs';
export function serverURL(value,allowHTTP=false){
 const url=new URL(value);
 if(!['https:','http:'].includes(url.protocol)||url.username||url.password||url.search||url.hash)throw Error('INVALID_XTREAM_SERVER');
 if(url.protocol==='http:'&&!allowHTTP)throw Error('HTTP_REQUIRES_EXPLICIT_APPROVAL');
 url.pathname=url.pathname.replace(/\/?$/,'/');
 return url;
}
export function xtreamURL(server,credentials,action,extra={}){
 if(!credentials.username||!credentials.password)throw Error('XTREAM_CREDENTIALS_REQUIRED');
 const url=new URL('player_api.php',server);
 url.search=new URLSearchParams({username:credentials.username,password:credentials.password,...(action?{action}:{}),...extra}).toString();
 return url;
}
export async function readSource(url){
 const response=await fetch(url,{redirect:'manual',headers:{'User-Agent':'NexusPlay/1.0','Accept':'application/json, application/x-mpegURL, */*'},signal:AbortSignal.timeout(20000)});
 if(!response.ok)throw Error('SOURCE_HTTP_'+response.status);
 const reader=response.body.getReader(),parts=[];let size=0;
 while(true){const p=await reader.read();if(p.done)break;size+=p.value.length;if(size>16*1024*1024){await reader.cancel();throw Error('SOURCE_TOO_LARGE');}parts.push(p.value);}
 const bytes=new Uint8Array(size);let offset=0;for(const p of parts){bytes.set(p,offset);offset+=p.length;}
 return new TextDecoder().decode(bytes);
}
const text=v=>v==null?'':String(v);
function image(value){try{const u=new URL(value);return ['http:','https:'].includes(u.protocol)?u.href:null;}catch{return null;}}
export function xtreamRows(entries,categories,section,server,credentials){
 const groups=new Map(categories.map(c=>[text(c.category_id),text(c.category_name)])),seen=new Set(),rows=[];let invalid=0,duplicates=0;
 for(const row of entries){
  const id=text(section==='series'?row.id:row.stream_id),title=text(row.title||row.name);
  if(!id||!title||!/^[a-zA-Z0-9_-]+$/.test(id)){invalid++;continue;}
  let extension=section==='live'?(credentials.live_extension||'ts'):text(row.container_extension)||'mp4';
  if(!/^[a-zA-Z0-9]{1,8}$/.test(extension)){invalid++;continue;}
  const path=[section,credentials.username,credentials.password,id+'.'+extension].map(encodeURIComponent).join('/');
  const stream_url=new URL(path,server).href;
  if(seen.has(stream_url)){duplicates++;continue;}seen.add(stream_url);
  rows.push({title,stream_url,content_type:section,group:groups.get(text(row.category_id))||text(row._group)||'Sem categoria',external_id:text(row.epg_channel_id)||section+':'+id,logo_url:image(row.stream_icon||row.cover||row.info?.movie_image)});
 }
 return {rows,invalid,duplicates};
}
export async function loadXtream(source,credentials,section='live',seriesIndex=0,categoryIndex=0){
 if(!['live','movie','series'].includes(section)||!Number.isInteger(seriesIndex)||seriesIndex<0||!Number.isInteger(categoryIndex)||categoryIndex<0)throw Error('INVALID_XTREAM_CURSOR');
 const server=serverURL(source,credentials.allow_insecure_http);
 async function json(action,extra){
  const raw=await readSource(xtreamURL(server,credentials,action,extra));
  try{return JSON.parse(raw);}catch{throw Error('XTREAM_INVALID_JSON');}
 }
 const auth=await json();
 if(text(auth.user_info?.auth)!=='1')throw Error('XTREAM_LOGIN_REJECTED');
 if(auth.user_info?.status&&text(auth.user_info.status).toLowerCase()!=='active')throw Error('XTREAM_ACCOUNT_INACTIVE');
 const categoryAction={live:'get_live_categories',movie:'get_vod_categories',series:'get_series_categories'}[section];
 const categories=await json(categoryAction);
 if(!Array.isArray(categories))throw Error('XTREAM_INVALID_CATEGORIES');
 categories.sort((a,b)=>text(a.category_id).localeCompare(text(b.category_id),'en',{numeric:true}));
 if(section!=='series'&&categoryIndex>=categories.length&&categories.length)throw Error('INVALID_XTREAM_CURSOR');
 const selectedCategory=section==='series'?null:categories[categoryIndex];
 const catalog=await json({live:'get_live_streams',movie:'get_vod_streams',series:'get_series'}[section],selectedCategory?{category_id:text(selectedCategory.category_id)}:undefined);
 if(!Array.isArray(catalog))throw Error('XTREAM_INVALID_CATALOG');
 let entries=catalog,nextSection=section==='live'?'movie':section==='movie'?'series':null,nextSeriesIndex=0,nextCategoryIndex=0;
 if(section!=='series'&&categoryIndex+1<categories.length){nextSection=section;nextCategoryIndex=categoryIndex+1;}
 if(section==='series'){
  // Sort series by stable identity so paginating providers with unstable ordering does not skip shows.
  catalog.sort((a,b)=>text(a.series_id).localeCompare(text(b.series_id),'en',{numeric:true}));
  if(seriesIndex>=catalog.length&&catalog.length)throw Error('INVALID_XTREAM_CURSOR');
  const show=catalog[seriesIndex];
  if(show){
   const info=await json('get_series_info',{series_id:text(show.series_id)});
   if(!info.episodes||typeof info.episodes!=='object')throw Error('XTREAM_INVALID_EPISODES');
   const group=categories.find(c=>text(c.category_id)===text(show.category_id))?.category_name;
   entries=Object.values(info.episodes).flat().map(ep=>({...ep,_group:group}));
   entries.sort((a,b)=>text(a.id).localeCompare(text(b.id),'en',{numeric:true}));
   if(seriesIndex+1<catalog.length){nextSection='series';nextSeriesIndex=seriesIndex+1;}
  }else entries=[];
 }
 const formats=auth.user_info.allowed_output_formats;
 const live_extension=Array.isArray(formats)&&!formats.includes('ts')&&formats.includes('m3u8')?'m3u8':'ts';
 const parsed=xtreamRows(entries,categories,section,server,{...credentials,live_extension});
 parsed.rows.sort((a,b)=>a.external_id.localeCompare(b.external_id,'en',{numeric:true}));
 if(entries.length&&!parsed.rows.length)throw Error('XTREAM_NO_VALID_STREAMS');
 const fingerprint=await sha256(JSON.stringify({source,section,seriesIndex,categoryIndex,rows:parsed.rows}));
 return {...parsed,fingerprint,nextSection,nextSeriesIndex,nextCategoryIndex,section,seriesIndex,categoryIndex};
}
