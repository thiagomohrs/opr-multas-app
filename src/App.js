import React, { Component } from 'react';
import logo from './logo.png';
import './App.css';

class App extends Component {
  render() {
    return (
      <div className="App">
        <header className="App-header">
          <img src={logo} className="App-logo" alt="logo" />
          <h1 className="App-title">Welcome to opr multas ALPHA</h1>
        </header>
        <p className="App-intro">
          Login:
        </p>
        <div className="App">
        <form className="login">
        <input placeholder="Login:" type="text" required/>
        <input placeholder="Senha" type="password" required/>
        <button onClick="validateForm()">Entrar</button>
        </form>
        </div>
      </div>
    );
  }
}

function validateForm(){
  alert(document.forms);
  var form = document.forms["login"].value;
  if (form === "") {
        alert("Form must be filled out");
        return false;
    }
}

export default App;
