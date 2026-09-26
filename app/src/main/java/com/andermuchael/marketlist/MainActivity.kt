package com.andermuchael.marketlist

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.serialization.Serializable
import java.text.NumberFormat
import java.util.Locale

@Serializable
data class ShoppingItem(val id: Long, val name: String, val price: Double, val quantity: Int = 1, val bought: Boolean = false) {
    val total get() = price * quantity
}

class MarketViewModel : ViewModel() {
    var items by mutableStateOf<List<ShoppingItem>>(emptyList()); private set
    var showAdd by mutableStateOf(false)
    var name by mutableStateOf("")
    var priceText by mutableStateOf("")
    var quantityText by mutableStateOf("1")
    val total get() = items.sumOf { it.total }
    val pending get() = items.count { !it.bought }
    val bought get() = items.count { it.bought }

    fun addItem() {
        val n=name.trim(); val p=priceText.replace(",",".").toDoubleOrNull() ?: 0.0
        val q=quantityText.toIntOrNull()?.coerceAtLeast(1) ?: 1
        if(n.isEmpty()) return
        items=items+ShoppingItem(System.currentTimeMillis(),n,p,q)
        name=""; priceText=""; quantityText="1"; showAdd=false
    }
    fun toggle(id:Long){items=items.map{if(it.id==id)it.copy(bought=!it.bought)else it}}
    fun delete(id:Long){items=items.filterNot{it.id==id}}
    fun clearBought(){items=items.filterNot{it.bought}}
}

fun money(v:Double)=NumberFormat.getCurrencyInstance(Locale("pt","BR")).format(v)

class MainActivity:ComponentActivity(){
    override fun onCreate(b:Bundle?){super.onCreate(b);setContent{MarketlistApp()}}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketlistApp(vm:MarketViewModel=viewModel()){
    MaterialTheme(colorScheme=lightColorScheme(
        primary=Color(0xFF2563EB), secondary=Color(0xFF10B981),
        background=Color(0xFFF6F8FC), surface=Color.White
    )){
        Scaffold(
            topBar={TopAppBar(title={Column{
                Text("Marketlist",style=MaterialTheme.typography.titleLarge)
                Text("Sua lista de compras",style=MaterialTheme.typography.labelMedium)
            }},actions={Icon(Icons.Default.ShoppingCart,null,Modifier.padding(end=18.dp))})},
            floatingActionButton={FloatingActionButton(onClick={vm.showAdd=true}){Icon(Icons.Default.Add,"Adicionar item")}}
        ){pad->
            Column(Modifier.fillMaxSize().padding(pad).padding(horizontal=16.dp)){
                Card(Modifier.fillMaxWidth(),RoundedCornerShape(24.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primary)){
                    Column(Modifier.padding(20.dp)){
                        Text("TOTAL DA COMPRA",style=MaterialTheme.typography.labelMedium,color=Color.White.copy(.75f))
                        Text(money(vm.total),style=MaterialTheme.typography.headlineMedium,color=Color.White)
                        Spacer(Modifier.height(10.dp))
                        Text("${vm.pending} pendentes  •  ${vm.bought} comprados",color=Color.White)
                    }
                }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End){
                    TextButton(onClick=vm::clearBought,enabled=vm.bought>0){Text("Limpar comprados")}
                }
                if(vm.items.isEmpty()){
                    Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){
                        Column(horizontalAlignment=Alignment.CenterHorizontally){
                            Icon(Icons.Default.ShoppingCart,null,Modifier.size(56.dp),tint=MaterialTheme.colorScheme.primary.copy(.55f))
                            Spacer(Modifier.height(12.dp))
                            Text("Sua lista está vazia",style=MaterialTheme.typography.titleLarge)
                            Text("Toque em + para adicionar um item.")
                        }
                    }
                }else LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(bottom=90.dp)){
                    items(vm.items,key={it.id}){item->
                        Card(Modifier.fillMaxWidth().alpha(if(item.bought).65f else 1f),RoundedCornerShape(18.dp)){
                            Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically){
                                IconButton(onClick={vm.toggle(item.id)}){
                                    Icon(if(item.bought)Icons.Default.CheckCircle else Icons.Default.ShoppingCart,
                                        if(item.bought)"Desmarcar" else "Marcar como comprado",
                                        tint=if(item.bought)MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary)
                                }
                                Column(Modifier.weight(1f)){
                                    Text(item.name,style=MaterialTheme.typography.titleMedium,
                                        textDecoration=if(item.bought)TextDecoration.LineThrough else TextDecoration.None)
                                    Text("${item.quantity} × ${money(item.price)}",style=MaterialTheme.typography.bodySmall)
                                }
                                Text(money(item.total),style=MaterialTheme.typography.titleMedium,
                                    textDecoration=if(item.bought)TextDecoration.LineThrough else TextDecoration.None)
                                IconButton(onClick={vm.delete(item.id)}){Icon(Icons.Default.Delete,"Excluir")}
                            }
                        }
                    }
                }
            }
        }
        if(vm.showAdd) AlertDialog(
            onDismissRequest={vm.showAdd=false},title={Text("Adicionar item")},
            text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
                OutlinedTextField(vm.name,{vm.name=it},label={Text("Item")},singleLine=true)
                OutlinedTextField(vm.priceText,{vm.priceText=it},label={Text("Valor unitário")},placeholder={Text("0,00")},singleLine=true)
                OutlinedTextField(vm.quantityText,{vm.quantityText=it},label={Text("Quantidade")},singleLine=true)
            }},
            confirmButton={Button(onClick=vm::addItem,enabled=vm.name.trim().isNotEmpty()){Text("Adicionar")}},
            dismissButton={TextButton(onClick={vm.showAdd=false}){Text("Cancelar")}}
        )
    }
}
