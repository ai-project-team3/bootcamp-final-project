// Shared axios instance. Every service imports this one.
// The common error shape is { error: true, message: "..." }.
import axios from "axios";

const client = axios.create({ baseURL: "/api", timeout: 15000 });

client.interceptors.response.use(
  (res) => res,
  (err) => {
    const message = err.response?.data?.message ?? "요청을 처리하지 못했습니다";
    return Promise.reject(new Error(message));
  },
);

export default client;
