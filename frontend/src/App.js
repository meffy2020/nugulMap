import React from "react";
import MainPage from "./components/mainPage";
import AdminPage from "./components/AdminPage";

function App() {
  // URL 경로에 따라 다른 컴포넌트를 렌더링
  const renderPage = () => {
    const path = window.location.pathname;
    if (path === '/admin') {
      return <AdminPage />;
    }
    return <MainPage />;
  };

  return (
    <div className="App">
      {renderPage()}
    </div>
  );
}

export default App;
