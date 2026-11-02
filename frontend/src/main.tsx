import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import App from "./App";
import "./styles/login.css";

// Spring Boot가 제공하는 단일 로그인 화면을 React 루트에 연결합니다. 루트가 없는 HTML이면 아무것도 하지 않습니다.
const rootElement = document.getElementById("root");
if (rootElement) {
  createRoot(rootElement).render(
    <StrictMode>
      <App />
    </StrictMode>,
  );
}
