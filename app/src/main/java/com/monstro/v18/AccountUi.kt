package com.monstro.v18

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth

private fun friendlyAuthError(raw:String?):String {
    val text=raw.orEmpty()
    return when {
        text.contains("CONFIGURATION_NOT_FOUND",true) ->
            "O login em nuvem ainda não foi ativado no projeto Firebase. Ative E-mail/Senha no Firebase Authentication; enquanto isso você pode usar um perfil local."
        text.contains("INVALID_LOGIN_CREDENTIALS",true) || text.contains("wrong-password",true) ->
            "E-mail ou senha incorretos."
        text.contains("email-already-in-use",true) ->
            "Esse e-mail já possui uma conta."
        text.contains("network",true) ->
            "Sem conexão com o serviço de conta. Verifique a internet."
        text.isBlank() -> "Não foi possível concluir a autenticação."
        else -> "Não foi possível concluir a autenticação agora."
    }
}

@Composable
fun AccountDialog(onClose:()->Unit){
    val context=LocalContext.current
    val auth=remember { FirebaseAuth.getInstance() }
    val localPrefs=remember {context.getSharedPreferences("monstro-account",0)}
    var user by remember { mutableStateOf(auth.currentUser) }
    var localEmail by remember {mutableStateOf(localPrefs.getString("localEmail",null))}
    var email by remember { mutableStateOf(user?.email ?: localEmail.orEmpty()) }
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
                when {
                    user!=null -> {
                        Text("Conta na nuvem",style=MaterialTheme.typography.labelMedium)
                        Text(user?.email ?: "Conta Firebase",style=MaterialTheme.typography.titleMedium)
                        Text("UID: ${user?.uid?.take(8)}…",style=MaterialTheme.typography.bodySmall)
                        if(status.isNotBlank())Text(status,style=MaterialTheme.typography.bodySmall)
                        Button(
                            onClick={auth.signOut();status="Você saiu da conta neste aparelho."},
                            enabled=!busy,
                            modifier=Modifier.fillMaxWidth()
                        ){Text("Sair / Logout")}
                    }
                    localEmail!=null -> {
                        Text("Perfil local",style=MaterialTheme.typography.labelMedium)
                        Text(localEmail.orEmpty(),style=MaterialTheme.typography.titleMedium)
                        Text("Projetos continuam salvos neste aparelho. Este perfil não sincroniza com a nuvem.",style=MaterialTheme.typography.bodySmall)
                        Button(
                            onClick={
                                localPrefs.edit().remove("localEmail").apply()
                                localEmail=null;email="";password="";status="Você saiu do perfil local."
                            },
                            modifier=Modifier.fillMaxWidth()
                        ){Text("Sair / Logout")}
                    }
                    else -> {
                        Text("Entre na nuvem ou continue editando como convidado.",style=MaterialTheme.typography.bodySmall)
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
                                    if(task.isSuccessful)finish("Conta conectada.") else finish(friendlyAuthError(task.exception?.message))
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
                                    if(task.isSuccessful)finish("Conta criada e conectada.") else finish(friendlyAuthError(task.exception?.message))
                                }
                            },
                            enabled=!busy,
                            modifier=Modifier.fillMaxWidth()
                        ){Text("Criar conta na nuvem")}
                        OutlinedButton(
                            onClick={
                                if(email.isBlank()){status="Digite um e-mail para identificar o perfil local.";return@OutlinedButton}
                                localPrefs.edit().putString("localEmail",email).apply()
                                localEmail=email;password="";status="Perfil local criado."
                            },
                            enabled=!busy,
                            modifier=Modifier.fillMaxWidth()
                        ){Text("Usar perfil local neste aparelho")}
                        TextButton(
                            onClick={
                                if(email.isBlank()){status="Digite seu e-mail primeiro.";return@TextButton}
                                busy=true
                                auth.sendPasswordResetEmail(email).addOnCompleteListener {task->
                                    if(task.isSuccessful)finish("Enviei o link de redefinição para seu e-mail.") else finish(friendlyAuthError(task.exception?.message))
                                }
                            },
                            enabled=!busy
                        ){Text("Esqueci minha senha")}
                        if(status.isNotBlank())Text(status,style=MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton={TextButton(onClick=onClose){Text("Fechar")}}
    )
}
