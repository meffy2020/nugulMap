import React from "react";
import ReactDOM from "react-dom/client"; // ReactDOM.createRoot를 사용하기 위해 ReactDOM.client import
import App from "./App";

const root = ReactDOM.createRoot(document.getElementById("root")); // createRoot 사용
root.render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);

