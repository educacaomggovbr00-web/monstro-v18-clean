package com.monstro.v18

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth

@Composable
fun AccountDialog(onClose:()->Unit){
    val auth=remember { FirebaseAuth.getInstance() }
    var user by remember { mutableStateOf(auth.currentUser) }
    var email by remember { mutableStateOf(user?.email.orEmpty()) }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }

    DisposableEffect(auth){
        val listener=FirebaseAuth.AuthStateListener { user=it.currentUser }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }

    fun finish(message:String){busy=false;status=message}
    AlertDialog(
        onDismissRequest=onClose,
        title={Text("Conta Monstro")},
        text={
            Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
                if(user!=null){
                    Text("Conectado como",style=MaterialTheme.typography.labelMedium)
                    Text(user?.email ?: "Conta Firebase",style=MaterialTheme.typography.titleMedium)
                    Text("UID: ${user?.uid?.take(8)}…",style=MaterialTheme.typography.bodySmall)
                    if(status.isNotBlank())Text(status,style=MaterialTheme.typography.bodySmall)
                    Button(
                        onClick={auth.signOut();status="Você saiu da conta neste aparelho."},
                        enabled=!busy,
                        modifier=Modifier.fillMaxWidth()
                    ){Text("Sair / Logout")}
                } else {
                    Text("Você pode continuar editando como convidado.",style=MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value=email,onValueChange={email=it.trim()},
                        label={Text("E-mail")},singleLine=true,
                        keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Email),
                        modifier=Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value=password,onValueChange={password=it},
                        label={Text("Senha")},singleLine=true,
                        visualTransformation=PasswordVisualTransformation(),
                        keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password),
                        modifier=Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick={
                            if(email.isBlank() || password.length<6){status="Use um e-mail válido e senha com pelo menos 6 caracteres.";return@Button}
                            busy=true;status="Entrando…"
                            auth.signInWithEmailAndPassword(email,password).addOnCompleteListener {task->
                                if(task.isSuccessful)finish("Conta conectada.") else finish("Não foi possível entrar: ${task.exception?.localizedMessage ?: "erro de autenticação"}")
                            }
                        },
                        enabled=!busy,
                        modifier=Modifier.fillMaxWidth()
                    ){Text(if(busy)"Aguarde…" else "Entrar")}
                    OutlinedButton(
                        onClick={
                            if(email.isBlank() || password.length<6){status="Use um e-mail válido e senha com pelo menos 6 caracteres.";return@OutlinedButton}
                            busy=true;status="Criando conta…"
                            auth.createUserWithEmailAndPassword(email,password).addOnCompleteListener {task->
                                if(task.isSuccessful)finish("Conta criada e conectada.") else finish("Não foi possível criar a conta: ${task.exception?.localizedMessage ?: "erro de autenticação"}")
                            }
                        },
                        enabled=!busy,
                        modifier=Modifier.fillMaxWidth()
                    ){Text("Criar conta")}
                    TextButton(
                        onClick={
                            if(email.isBlank()){status="Digite seu e-mail primeiro.";return@TextButton}
                            busy=true
                            auth.sendPasswordResetEmail(email).addOnCompleteListener {task->
                                if(task.isSuccessful)finish("Enviei o link de redefinição para seu e-mail.") else finish("Falha ao enviar redefinição: ${task.exception?.localizedMessage ?: "erro"}")
                            }
                        },
                        enabled=!busy
                    ){Text("Esqueci minha senha")}
                    if(status.isNotBlank())Text(status,style=MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton={TextButton(onClick=onClose){Text("Fechar")}}
    )
}
