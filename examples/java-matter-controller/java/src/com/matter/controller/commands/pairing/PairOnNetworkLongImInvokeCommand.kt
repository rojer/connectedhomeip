/*
 *   Copyright (c) 2023 Project CHIP Authors
 *   All rights reserved.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 */
package com.matter.controller.commands.pairing

import chip.devicecontroller.ChipDeviceController
import chip.devicecontroller.GetConnectedDeviceCallbackJni.GetConnectedDeviceCallback
import chip.devicecontroller.InvokeCallback
import chip.devicecontroller.model.InvokeElement
import chip.tlv.AnonymousTag
import chip.tlv.ContextSpecificTag
import chip.tlv.TlvWriter
import com.matter.controller.commands.common.CredentialsIssuer
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.UShort

class PairOnNetworkLongImInvokeCommand(
  controller: ChipDeviceController,
  credsIssue: CredentialsIssuer?
) :
  PairingCommand(
    controller,
    "onnetwork-long-im-invoke",
    credsIssue,
    PairingModeType.ON_NETWORK,
    PairingNetworkType.NONE,
    DiscoveryFilterType.LONG_DISCRIMINATOR
  ) {
  private var devicePointer: Long = 0

  private fun setDevicePointer(devicePointer: Long) {
    this.devicePointer = devicePointer
  }

  private inner class InternalInvokeCallback : InvokeCallback {
    override fun onError(e: Exception) {
      logger.log(Level.INFO, "Invoke receive onError" + e.message)
      setFailure("invoke failure")
    }

    override fun onResponse(element: InvokeElement?, successCode: Long) {
      logger.log(Level.INFO, "Invoke receive OnResponse on ")
      if (element != null) {
        logger.log(Level.INFO, element.toString() + element.getJsonString())
        val clusterId = element.getClusterId().getId()
        if (clusterId == CLUSTER_ID_IDENTIFY) {
          logger.log(Level.INFO, "success code is $successCode")
          setSuccess()
          return
        } else if (
          clusterId == CLUSTER_ID_TEST && element.getJsonString().equals("""{"0:UINT":2}""")
        ) {
          logger.log(Level.INFO, "success code is $successCode")
          setSuccess()
          return
        }
      }

      setFailure("invoke failure")
    }
  }

  private inner class InternalGetConnectedDeviceCallback : GetConnectedDeviceCallback {
    override fun onDeviceConnected(devicePointer: Long) {
      setDevicePointer(devicePointer)
      logger.log(Level.INFO, "onDeviceConnected")
    }

    override fun onConnectionFailure(nodeId: Long, error: Exception) {
      logger.log(Level.INFO, "onConnectionFailure")
    }
  }

  override fun runCommand() {
    val number: UShort = 1u
    val tlvWriter1 = TlvWriter()
    tlvWriter1.startStructure(AnonymousTag)
    tlvWriter1.put(ContextSpecificTag(0), number)
    tlvWriter1.endStructure()

    val element1: InvokeElement =
      InvokeElement.newInstance(
        /* endpointId= */ 0,
        CLUSTER_ID_IDENTIFY,
        IDENTIFY_COMMAND,
        tlvWriter1.getEncoded(),
        null
      )

    val tlvWriter2 = TlvWriter()
    tlvWriter2.startStructure(AnonymousTag)
    tlvWriter2.put(ContextSpecificTag(0), number)
    tlvWriter2.put(ContextSpecificTag(1), number)
    tlvWriter2.endStructure()

    val element2: InvokeElement =
      InvokeElement.newInstance(
        /* endpointId= */ 1,
        CLUSTER_ID_TEST,
        TEST_ADD_ARGUMENT_COMMAND,
        tlvWriter2.getEncoded(),
        null
      )

    val element3: InvokeElement =
      InvokeElement.newInstance(
        /* endpointId= */ 1,
        CLUSTER_ID_IDENTIFY,
        IDENTIFY_COMMAND,
        null,
        """{"0:UINT":1}"""
      )

    currentCommissioner()
      .pairDeviceWithAddress(
        getNodeId(),
        getRemoteAddr().getHostAddress(),
        MATTER_PORT,
        getDiscriminator(),
        getSetupPINCode(),
        null
      )
    currentCommissioner().setCompletionListener(this)
    waitCompleteMs(getTimeoutMillis())
    currentCommissioner()
      .getConnectedDevicePointer(getNodeId(), InternalGetConnectedDeviceCallback())
    clear()
    currentCommissioner().invoke(InternalInvokeCallback(), devicePointer, element1, 0, 0)
    waitCompleteMs(getTimeoutMillis())
    clear()
    currentCommissioner().invoke(InternalInvokeCallback(), devicePointer, element2, 0, 0)
    waitCompleteMs(getTimeoutMillis())
    clear()
    currentCommissioner().invoke(InternalInvokeCallback(), devicePointer, element3, 0, 0)
    waitCompleteMs(getTimeoutMillis())
  }

  companion object {
    private val logger = Logger.getLogger(PairOnNetworkLongImInvokeCommand::class.java.name)

    private const val MATTER_PORT = 5540
    private const val CLUSTER_ID_IDENTIFY = 0x0003L
    private const val IDENTIFY_COMMAND = 0L
    private const val CLUSTER_ID_TEST = 0xFFF1FC05L
    private const val TEST_ADD_ARGUMENT_COMMAND = 0X04L
  }
}
