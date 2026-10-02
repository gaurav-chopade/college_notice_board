import { useEffect, useState } from 'react';
import { PublicApi } from '../services/api';

// College details and the two wings change rarely: fetch once per page load and share.
const cache = {};
function shared(key, load) {
  if (!cache[key]) cache[key] = load().catch((e) => { delete cache[key]; throw e; });
  return cache[key];
}

function useShared(key, load) {
  const [data, setData] = useState(null);
  useEffect(() => {
    let alive = true;
    shared(key, load).then((d) => { if (alive) setData(d); }).catch(() => {});
    return () => { alive = false; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key]);
  return data;
}

export const useCollege = () => useShared('college', PublicApi.college);
export const useInstitutions = () => useShared('institutions', PublicApi.institutions);
export const usePublicCategories = () => useShared('categories', PublicApi.categories);
