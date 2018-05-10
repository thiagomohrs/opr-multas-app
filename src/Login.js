import React, { Component } from 'react';
import './Login.css';

class Login extends Component{
    constructor(props){
        super(props);
        this.state = {usuario:''};
        this.validateForm = this.validateForm.bind(this);
    }
    render(){
        return(
        <div>
            <p className="App-intro">
                Login:
            </p>
            <form className="login">
            <input id="login" placeholder="Login:" type="text" required/>
            &nbsp;&nbsp;
            <input id="senha" placeholder="Senha" type="password" required/>
            &nbsp;&nbsp;
            <button onClick={this.validateForm}>Entrar</button>
            </form>
            <div>{this.state.usuario}</div>
        </div>
        )
    }
    validateForm() {
        var login = document.getElementById("login").value;
        var senha = document.getElementById("login").value;
        if(login === "asd" && senha === "asd"){
            this.setState({ usuario: "Login efetuado" });
        } else {
            this.setState({ usuario: "Usuário não cadastrado" });
        }
    }
};

export default Login;