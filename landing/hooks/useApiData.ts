"use client";

import useSWR from "swr";
import { fetchApi } from "@/lib/api";

interface UseApiDataResult<T> {
  data: T | undefined;
  error: Error | undefined;
  isLoading: boolean;
}

export function useApiData<T>(path: string): UseApiDataResult<T> {
  const { data, error, isLoading } = useSWR<T>(
    path,
    (url: string) => fetchApi<T>(url),
    {
      revalidateOnFocus: false,
      dedupingInterval: 30000,
    }
  );

  return {
    data,
    error,
    isLoading,
  };
}
